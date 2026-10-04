import json
from pathlib import Path
import tempfile
import unittest
from types import SimpleNamespace
from consumption import node


class NodeOwnershipTest(unittest.TestCase):
    def api(self, items, checkpoint=None):
        return SimpleNamespace(project_id=7, provisioned_nodes=checkpoint or {},
                               request=lambda method, path, body=None: items)

    def test_existing_foreign_unconfigured_node_is_not_owned(self):
        item = {"id": "99", "name": "Sample", "type": "DATASET"}
        value, owned = node(self.api([item]), "Sample", "DATASET")
        self.assertEqual(item, value)
        self.assertFalse(owned)

    def test_checkpoint_allows_resume_only_same_identity(self):
        item = {"id": "99", "name": "Sample", "type": "DATASET"}
        self.assertTrue(node(self.api([item], {"Sample": "99"}), "Sample", "DATASET")[1])
        self.assertFalse(node(self.api([item], {"Sample": "98"}), "Sample", "DATASET")[1])

    def test_type_collision_and_duplicate_name_are_rejected(self):
        with self.assertRaises(ValueError):
            node(self.api([{"id": "99", "name": "Sample", "type": "DATA_SERVICE"}]), "Sample", "DATASET")
        with self.assertRaises(ValueError):
            node(self.api([{"id": "99", "name": "Sample"}, {"id": "98", "name": "Sample"}]), "Sample", "DATASET")

    def test_creation_checkpoints_identity_before_source_configuration(self):
        with tempfile.TemporaryDirectory() as folder:
            api = self.api([])
            api.progress_path = Path(folder) / "progress.local.json"
            api.request = lambda method, path, body=None: [] if method == "GET" else {"id": "99", **body}
            value, owned = node(api, "Sample", "DATASET")
            self.assertTrue(owned)
            self.assertEqual("99", value["id"])
            self.assertEqual({"Sample": "99"}, json.loads(api.progress_path.read_text())["nodes"])


if __name__ == "__main__":
    unittest.main()
