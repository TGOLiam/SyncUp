"""Allow only listen-only LiveKit tokens to initiate a local signaling session."""

import asyncio
import urllib.parse

import jwt


MAX_HEADER_BYTES = 16384


def allowed_token(token, api_key, api_secret):
    try:
        claims = jwt.decode(token, api_secret, algorithms=["HS256"], issuer=api_key)
        video = claims.get("video")
        return (
            isinstance(video, dict)
            and video.get("roomJoin") is True
            and video.get("canSubscribe") is True
            and video.get("canPublish") is False
            and video.get("canPublishData") is False
        )
    except (jwt.PyJWTError, TypeError, ValueError):
        return False


def extract_token(target, header_lines):
    query = urllib.parse.parse_qs(urllib.parse.urlsplit(target).query, keep_blank_values=True)
    query_tokens = query.get("access_token", [])
    if len(query_tokens) > 1:
        raise ValueError("Ambiguous token")
    authorization = []
    for line in header_lines:
        name, separator, value = line.partition(":")
        if separator and name.lower() == "authorization":
            authorization.append(value.strip())
    if len(authorization) > 1:
        raise ValueError("Ambiguous authorization")
    header_token = None
    if authorization:
        if not authorization[0].startswith("Bearer "):
            raise ValueError("Invalid authorization")
        header_token = authorization[0][len("Bearer "):]
    query_token = query_tokens[0] if query_tokens else None
    if query_token is not None and header_token is not None and query_token != header_token:
        raise ValueError("Conflicting tokens")
    return query_token if query_token is not None else header_token


async def relay(reader, writer):
    try:
        while data := await reader.read(65536):
            writer.write(data)
            await writer.drain()
    except (ConnectionError, OSError):
        pass
    finally:
        writer.close()


async def handle(client_reader, client_writer, api_key, api_secret):
    upstream_writer = None
    try:
        header = await asyncio.wait_for(client_reader.readuntil(b"\r\n\r\n"), timeout=5)
        if len(header) > MAX_HEADER_BYTES:
            raise ValueError("Header too large")
        lines = header.decode("iso-8859-1").split("\r\n")
        method, target, _ = lines[0].split(" ", 2)
        parsed = urllib.parse.urlsplit(target)
        if method != "GET" or parsed.path not in ("/rtc", "/rtc/validate", "/rtc/v1", "/rtc/v1/validate"):
            route = parsed.path if parsed.path in ("/rtc", "/rtc/validate", "/rtc/v1", "/rtc/v1/validate", "/rtc/signal", "/twirp/livekit.Signal/StartSession", "/") else "other"
            verb = method if method in ("GET", "POST", "HEAD") else "other"
            print("Signaling rejected:", verb, route, flush=True)
            raise ValueError("Unexpected signaling request")
        token = extract_token(target, lines[1:])
        if not token or not allowed_token(token, api_key, api_secret):
            print("Signaling rejected:", "missing token" if not token else "invalid token or grants", flush=True)
            raise ValueError("Token is not listen-only")
        upstream_reader, upstream_writer = await asyncio.open_connection("127.0.0.1", 7880)
        upstream_writer.write(header)
        await upstream_writer.drain()
        await asyncio.gather(
            relay(client_reader, upstream_writer),
            relay(upstream_reader, client_writer),
        )
    except (ValueError, asyncio.TimeoutError, asyncio.IncompleteReadError,
            asyncio.LimitOverrunError, ConnectionError, OSError):
        try:
            client_writer.write(b"HTTP/1.1 403 Forbidden\r\nContent-Length: 0\r\nConnection: close\r\n\r\n")
            await client_writer.drain()
        except (ConnectionError, OSError):
            pass
    finally:
        if upstream_writer is not None:
            upstream_writer.close()
        client_writer.close()


async def serve(api_key, api_secret):
    server = await asyncio.start_server(
        lambda reader, writer: handle(reader, writer, api_key, api_secret),
        "127.0.0.1", 7883, limit=MAX_HEADER_BYTES,
    )
    async with server:
        await server.serve_forever()
