import base64
import importlib.util
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('pipeline', Path(__file__).parents[1] / 'pipeline.py')
p = importlib.util.module_from_spec(spec)
spec.loader.exec_module(p)
SHA = 'a' * 40
MERGE = 'b' * 40
TREE = 'c' * 40
TAG = 'teste-pr2-r1'


class API:
    repo = 'owner/repo'

    def __init__(self):
        self.pr = {'number': 2, 'state': 'open', 'merged': False, 'merged_at': None,
                   'head': {'sha': SHA, 'repo': {'full_name': self.repo}},
                   'base': {'ref': 'work', 'repo': {'full_name': self.repo}},
                   'merge_commit_sha': MERGE}
        self.permission = 'admin'
        self.check = 'success'
        self.latest_check_status = 'completed'
        self.comments = []
        self.releases = []
        self.merge_tree = TREE
        self.tip = MERGE
        self.candidate = {'id': 1, 'draft': False, 'prerelease': True, 'tag_name': TAG,
                          'published_at': '2026-10-27T10:00:00Z'}
        self.run = {'path': '.github/workflows/distribute.yml', 'event': 'workflow_dispatch',
                    'head_branch': 'work', 'status': 'completed', 'conclusion': 'success', 'run_number': 1}
        self.metadata = {'schema': 1, 'mode': 'test', 'pr': 2, 'source_sha': SHA, 'source_tree': TREE,
                         'tag': TAG, 'run_id': 1, 'version_code': 1001, 'apk': 'Meu-Financeiro.apk',
                         'apk_sha256': p.hashlib.sha256(b'apk').hexdigest()}
        self.version = '0.1.1'
        self.previous = dict(self.metadata)

    def request(self, path, method='GET', data=None, raw=False):
        if path.startswith('/collaborators/'):
            return {'permission': self.permission}
        if path == '':
            return {'visibility': 'public', 'default_branch': 'work'}
        if path == '/pulls/2':
            return self.pr
        if path.startswith('/actions/workflows/'):
            return {'workflow_runs': [{'id': 1, 'status': self.latest_check_status, 'conclusion': self.check}]}
        if path.startswith('/git/commits/'):
            return {'tree': {'sha': self.merge_tree if path.endswith(MERGE) else TREE}}
        if path.startswith('/contents/version.properties'):
            return {'content': base64.b64encode(f'versionName={self.version}\n'.encode()).decode()}
        if path.startswith('/releases/tags/'):
            return self.candidate
        if path == '/actions/runs/1':
            return self.run
        if path == '/git/ref/heads/work':
            return {'object': {'sha': self.tip}}
        if path.startswith('/compare/'):
            return {'status': 'ahead'}
        raise AssertionError(path)

    def pages(self, path):
        if path == '/releases':
            return self.releases
        if path == '/issues/2/comments':
            return self.comments
        raise AssertionError(path)

    def asset(self, release, name):
        metadata = self.previous if release['id'] == 3 else self.metadata
        return json.dumps(metadata).encode() if name == 'pipeline.json' else b'apk'

    def approved(self):
        self.pr.update(state='closed', merged=True, merged_at='2026-10-27T12:00:00Z')
        self.comments = [{'id': 1, 'user': {'login': 'owner'}, 'body': f'Aprovado para publicação: {TAG} {SHA}',
                          'created_at': '2026-10-27T11:00:00Z', 'updated_at': '2026-10-27T11:00:00Z'}]
        return self


class Gates(unittest.TestCase):
    def setUp(self):
        self.env = patch.dict(os.environ, {'GITHUB_REF': 'refs/heads/work', 'GITHUB_ACTOR': 'owner',
                            'GITHUB_TRIGGERING_ACTOR': 'owner', 'GITHUB_RUN_NUMBER': '2', 'GITHUB_RUN_ID': '20'})
        self.env.start()
        self.addCleanup(self.env.stop)
        self.api = API()

    def blocked(self, mode='release'):
        with self.assertRaises(p.Blocked):
            p.resolve(self.api, mode, 2)

    def test_open_pr_candidate(self):
        result = p.resolve(self.api, 'test', 2)
        self.assertEqual(result['source_sha'], SHA)
        self.assertEqual(result['version_code'], 1002)
        self.assertEqual(result['tag'], 'teste-pr2-r2')

    def test_exact_approved_tree_can_release(self):
        self.api.approved()
        result = p.resolve(self.api, 'release', 2)
        self.assertEqual(result['source_sha'], MERGE)
        self.assertEqual(result['candidate_tag'], TAG)

    def test_release_requires_merge(self):
        self.blocked()

    def test_release_requires_approval(self):
        self.api.approved().comments = []
        self.blocked()

    def test_different_commit_invalidates_approval(self):
        self.api.approved().pr['head']['sha'] = 'd' * 40
        self.blocked()

    def test_changed_merge_tree_requires_retest(self):
        self.api.approved().merge_tree = 'd' * 40
        self.blocked()

    def test_old_pr_cannot_replace_new_tip(self):
        self.api.approved().tip = 'd' * 40
        self.blocked()

    def test_failed_ci_blocks(self):
        self.api.check = 'failure'
        self.blocked('test')

    def test_pending_ci_blocks(self):
        self.api.latest_check_status = 'in_progress'
        self.blocked('test')

    def test_actor_must_have_write_access(self):
        self.api.permission = 'read'
        self.blocked('test')

    def test_dispatch_only_from_work(self):
        os.environ['GITHUB_REF'] = 'refs/heads/feature'
        self.blocked('test')

    def test_forks_cannot_receive_signing_credentials(self):
        self.api.pr['head']['repo']['full_name'] = 'stranger/repo'
        self.blocked('test')

    def test_approval_before_candidate_is_invalid(self):
        self.api.approved().comments[0]['created_at'] = '2026-10-26T10:00:00Z'
        self.blocked()

    def test_approval_edited_after_merge_is_invalid(self):
        self.api.approved().comments[0]['updated_at'] = '2026-10-28T10:00:00Z'
        self.blocked()

    def test_failed_candidate_run_cannot_be_approved(self):
        self.api.approved().run['conclusion'] = 'failure'
        self.blocked()

    def test_other_workflow_cannot_forge_candidate(self):
        self.api.approved().run['path'] = '.github/workflows/other.yml'
        self.blocked()

    def test_candidate_apk_digest_is_checked(self):
        self.api.approved().metadata['apk_sha256'] = '0' * 64
        self.blocked()

    def test_draft_is_not_a_tested_candidate(self):
        self.api.approved().candidate['draft'] = True
        self.blocked()

    def test_candidate_version_code_matches_shared_counter(self):
        self.api.approved().metadata['version_code'] = 7
        self.blocked()

    def test_published_version_is_immutable(self):
        self.api.approved().releases = [{'id': 3, 'draft': False, 'prerelease': False, 'tag_name': 'v0.1.1'}]
        self.blocked()

    def test_baseline_requires_new_version(self):
        self.api.version = '0.1.0'
        self.blocked('test')

    def test_older_version_code_is_blocked(self):
        self.api.approved().releases = [{'id': 3, 'draft': False, 'prerelease': False, 'tag_name': 'v0.1.0'}]
        self.api.previous['version_code'] = 1003
        self.blocked()

    def test_pagination_reads_all_approval_evidence(self):
        api = p.GitHub.__new__(p.GitHub)
        api.request = lambda path: list(range(100)) if path.endswith('&page=1') else [100]
        self.assertEqual(len(api.pages('/issues/2/comments')), 101)

    def test_redirect_drops_token_on_other_host(self):
        req = p.urllib.request.Request('https://api.github.com/a', headers={'Authorization': 'Bearer test'})
        new = p.SafeRedirect().redirect_request(req, None, 302, '', {}, 'https://example.org/asset')
        self.assertIsNone(new.get_header('Authorization'))


if __name__ == '__main__':
    unittest.main()
