"""Exercise draft handoff/readback and publication failure boundaries."""
import importlib.util
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('pipeline_publication', Path(__file__).parents[1] / 'pipeline.py')
p = importlib.util.module_from_spec(spec)
spec.loader.exec_module(p)


class API:
    repo = 'owner/repo'

    def __init__(self):
        self.release = {'id': 5, 'tag_name': 'teste-pr2-r2', 'draft': True, 'prerelease': True,
                        'body': 'Transport run 20', 'upload_url': 'https://uploads.github.com/upload{?name}',
                        'html_url': 'https://github.com/owner/repo/releases/tag/teste-pr2-r2'}
        self.assets = {'unsigned.apk': b'unsigned'}
        self.history = []
        self.corrupt_readback = False
        self.releases = []

    def request(self, path, method='GET', data=None, raw=False):
        self.history.append((method, path))
        if path == '/releases/5' and method == 'GET':
            return self.release
        if path == '/releases/5' and method == 'PATCH':
            self.release.update(data)
            return self.release
        if path.startswith('/releases/assets/') and method == 'DELETE':
            name = next(k for k in self.assets if str(sum(k.encode())) == path.rsplit('/', 1)[1])
            del self.assets[name]
            return
        if path == '/releases/5' and method == 'DELETE':
            return
        if path == '/releases' and method == 'POST':
            self.release.update(data)
            return self.release
        raise AssertionError((path, method))

    def pages(self, path):
        if path == '/releases/5/assets':
            return [{'id': sum(name.encode()), 'name': name} for name in self.assets]
        if path == '/releases':
            return self.releases
        raise AssertionError(path)

    def upload(self, release, path):
        self.assets[path.name] = path.read_bytes()

    def asset(self, release, name):
        return b'corrupted' if self.corrupt_readback else self.assets[name]


class Publication(unittest.TestCase):
    def setUp(self):
        self.api = API()
        self.plan = {'mode': 'test', 'pr': 2, 'source_sha': 'a' * 40, 'version_code': 1002,
                     'version_name': '0.1.1', 'tag': 'teste-pr2-r2', 'run_id': 20, 'change_title': 'Feature'}
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.output = Path(self.temp.name)
        (self.output / 'Meu-Financeiro.apk').write_bytes(b'signed')
        self.env = patch.dict(os.environ, {'GITHUB_STEP_SUMMARY': str(self.output / 'summary')})
        self.env.start()
        self.addCleanup(self.env.stop)

    def publish(self):
        with patch.object(p, 'resolve', return_value=dict(self.plan)):
            p.publish(self.api, self.plan, 5, self.output)

    def test_only_verified_signed_assets_become_public(self):
        self.publish()
        self.assertFalse(self.api.release['draft'])
        self.assertEqual(set(self.api.assets), {'Meu-Financeiro.apk', 'pipeline.json', 'SHA256SUMS'})
        meta = json.loads(self.api.assets['pipeline.json'])
        self.assertEqual(meta['apk_sha256'], p.hashlib.sha256(b'signed').hexdigest())
        self.assertIn('Aprovado para publicação:', self.api.release['body'])

    def test_corrupt_upload_never_publishes(self):
        self.api.corrupt_readback = True
        with self.assertRaises(p.Blocked):
            self.publish()
        self.assertTrue(self.api.release['draft'])
        self.assertNotIn(('PATCH', '/releases/5'), self.api.history)

    def test_changed_evidence_never_publishes(self):
        changed = dict(self.plan, source_sha='b' * 40)
        with patch.object(p, 'resolve', side_effect=[dict(self.plan), changed]):
            with self.assertRaises(p.Blocked):
                p.publish(self.api, self.plan, 5, self.output)
        self.assertTrue(self.api.release['draft'])

    def test_stage_is_private_and_hashes_transferred_bytes(self):
        unsigned = self.output / 'unsigned.apk'
        unsigned.write_bytes(b'unsigned')
        release_id, checksum = p.stage(self.api, self.plan, unsigned)
        self.assertEqual(release_id, 5)
        self.assertEqual(checksum, p.hashlib.sha256(b'unsigned').hexdigest())
        self.assertTrue(self.api.release['draft'])

    def test_stage_cannot_replace_published_release(self):
        self.api.releases = [dict(self.api.release, draft=False)]
        with self.assertRaises(p.Blocked):
            p.stage(self.api, self.plan, self.output / 'Meu-Financeiro.apk')
        self.assertFalse(any(method == 'DELETE' for method, _ in self.api.history))

    def test_stage_cannot_replace_another_runs_draft(self):
        self.api.releases = [dict(self.api.release, body='Transport run 19')]
        with self.assertRaises(p.Blocked):
            p.stage(self.api, self.plan, self.output / 'Meu-Financeiro.apk')
        self.assertFalse(any(method == 'DELETE' for method, _ in self.api.history))


if __name__ == '__main__':
    unittest.main()
