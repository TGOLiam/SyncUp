import importlib.util
import unittest
from pathlib import Path
from unittest.mock import patch


spec = importlib.util.spec_from_file_location("deployer", Path(__file__).with_name("syncup-deploy.py"))
deployer = importlib.util.module_from_spec(spec)
spec.loader.exec_module(deployer)


class DeploymentGateTest(unittest.TestCase):
    def setUp(self):
        self.commit = "a" * 40
        self.run = {
            "head_sha": self.commit,
            "head_branch": "main",
            "event": "push",
            "status": "completed",
            "conclusion": "success",
            "head_repository": {"full_name": deployer.REPOSITORY},
        }

    def test_only_matching_successful_push_is_deployed(self):
        self.assertTrue(deployer.ci_passed(self.commit, {"workflow_runs": [self.run]}))
        for field, invalid in (
            ("head_sha", "b" * 40),
            ("head_branch", "other"),
            ("event", "pull_request"),
            ("status", "in_progress"),
            ("conclusion", "failure"),
            ("head_repository", {"full_name": "other/repository"}),
        ):
            changed = {**self.run, field: invalid}
            self.assertFalse(deployer.ci_passed(self.commit, {"workflow_runs": [changed]}), field)
        self.assertFalse(deployer.ci_passed(self.commit, {"workflow_runs": []}))

    def test_newer_failure_is_not_hidden_by_previous_success(self):
        failed = {**self.run, "conclusion": "failure"}
        self.assertFalse(deployer.ci_passed(self.commit, {"workflow_runs": [failed, self.run]}))

    def test_missing_configuration_never_deploys(self):
        with patch.object(deployer, "checked_config", return_value=False), \
             patch.object(deployer, "command") as command:
            deployer.main()
            command.assert_not_called()


if __name__ == "__main__":
    unittest.main()
