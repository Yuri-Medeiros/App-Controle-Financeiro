#!/usr/bin/env python3
"""Trusted distribution control plane. Never execute this file from a PR checkout."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import urllib.error
import urllib.parse
import urllib.request


class Blocked(RuntimeError):
    pass


class APIError(Blocked):
    def __init__(self, status, message):
        super().__init__(message)
        self.status = status


def require(condition, message):
    if not condition:
        raise Blocked(message)


class SafeRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        redirected = super().redirect_request(req, fp, code, msg, headers, newurl)
        if redirected and urllib.parse.urlsplit(req.full_url).netloc != urllib.parse.urlsplit(newurl).netloc:
            redirected.remove_header('Authorization')
        return redirected


class GitHub:
    def __init__(self):
        self.repo = os.environ['GITHUB_REPOSITORY']
        self.base = f'https://api.github.com/repos/{self.repo}'
        self.opener = urllib.request.build_opener(SafeRedirect())

    def request(self, path, method='GET', data=None, raw=False):
        url = path if path.startswith('https://') else self.base + path
        require(urllib.parse.urlsplit(url).hostname in ('api.github.com', 'uploads.github.com'), 'Untrusted API host')
        headers = {'Authorization': 'Bearer ' + os.environ['GH_TOKEN'],
                   'Accept': 'application/octet-stream' if raw else 'application/vnd.github+json',
                   'X-GitHub-Api-Version': '2022-11-28'}
        if data is not None:
            if isinstance(data, bytes):
                headers['Content-Type'] = 'application/octet-stream'
            else:
                headers['Content-Type'] = 'application/json'
                data = json.dumps(data).encode()
        req = urllib.request.Request(url, data=data, method=method, headers=headers)
        try:
            with self.opener.open(req, timeout=120) as response:
                result = response.read()
            return result if raw else (json.loads(result) if result else None)
        except urllib.error.HTTPError as error:
            raise APIError(error.code, f'GitHub HTTP {error.code}: {method} {urllib.parse.urlsplit(url).path}') from None

    def pages(self, path):
        results = []
        for page in range(1, 101):
            batch = self.request(f'{path}{"&" if "?" in path else "?"}per_page=100&page={page}')
            results.extend(batch)
            if len(batch) < 100:
                return results
        raise Blocked('Pagination limit reached; refusing partial evidence')

    def asset(self, release, name):
        assets = self.pages(f'/releases/{release["id"]}/assets')
        found = [a for a in assets if a['name'] == name]
        require(len(found) == 1, f'Missing or ambiguous release asset: {name}')
        return self.request(f'/releases/assets/{found[0]["id"]}', raw=True)

    def upload(self, release, path):
        url = release['upload_url'].split('{')[0] + '?name=' + urllib.parse.quote(path.name)
        return self.request(url, 'POST', path.read_bytes())


def authorized(api, login):
    permission = api.request('/collaborators/' + urllib.parse.quote(login, safe='') + '/permission')['permission']
    return permission in ('admin', 'maintain', 'write')


def tree(api, sha):
    require(bool(re.fullmatch('[0-9a-f]{40}', sha)), 'Invalid commit SHA')
    return api.request('/git/commits/' + sha)['tree']['sha']


def version_at(api, sha):
    import base64
    content = api.request('/contents/version.properties?ref=' + sha)
    text = base64.b64decode(content['content']).decode()
    matches = re.findall(r'^versionName=(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$', text, re.M)
    require(len(matches) == 1, 'version.properties must contain one semantic version')
    return '.'.join(matches[0])


def version_tuple(value):
    require(bool(re.fullmatch(r'(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)', value)), 'Invalid version')
    return tuple(map(int, value.split('.')))


def check_checks(api, sha):
    # Require the exact, named CI workflow, not a similarly named external status.
    runs = api.request('/actions/workflows/ci.yml/runs?head_sha=' + sha + '&event=pull_request&per_page=100')['workflow_runs']
    require(runs, 'No PR CI run for the tested commit')
    latest = max(runs, key=lambda r: r['id'])
    require(latest['status'] == 'completed' and latest['conclusion'] == 'success', 'Latest PR checks have not passed')


def approval(api, pr_number, source_sha, merged_at):
    pattern = re.compile(r'Aprovado para publicação: (teste-pr' + str(pr_number) + r'-r[0-9]+) (' + source_sha + r')')
    approved = []
    for comment in api.pages(f'/issues/{pr_number}/comments'):
        match = pattern.fullmatch(comment['body'].strip())
        if match and comment['updated_at'] <= merged_at and authorized(api, comment['user']['login']):
            approved.append((comment, match[1]))
    require(approved, 'Missing authorized approval for the exact candidate and commit, recorded before merge')
    return max(approved, key=lambda pair: pair[0]['id'])


def validate_candidate(api, release, metadata, pr, comment):
    require(not release['draft'] and release['prerelease'], 'Candidate is not a published prerelease')
    require(metadata['mode'] == 'test' and metadata['pr'] == pr['number'], 'Candidate PR mismatch')
    require(metadata['source_sha'] == pr['head']['sha'], 'Candidate commit changed')
    require(metadata['source_tree'] == tree(api, pr['head']['sha']), 'Candidate tree changed')
    require(metadata['tag'] == release['tag_name'], 'Candidate tag mismatch')
    require(comment['created_at'] >= release['published_at'], 'Approval predates the candidate')
    require(comment['updated_at'] <= pr['merged_at'], 'Approval was changed after merge')
    run = api.request('/actions/runs/' + str(metadata['run_id']))
    require(run['path'] == '.github/workflows/distribute.yml' and run['event'] == 'workflow_dispatch'
            and run['head_branch'] == 'work' and run['status'] == 'completed' and run['conclusion'] == 'success',
            'Candidate distribution did not complete successfully from work')
    require(run['run_number'] + 1000 == metadata['version_code'], 'Candidate version code mismatch')
    apk = api.asset(release, metadata['apk'])
    require(hashlib.sha256(apk).hexdigest() == metadata['apk_sha256'], 'Candidate APK checksum mismatch')


def resolve(api, mode, pr_number):
    require(os.environ.get('GITHUB_REF') == 'refs/heads/work', 'Run this workflow from work')
    require(authorized(api, os.environ['GITHUB_ACTOR']), 'Actor is not allowed to distribute')
    require(authorized(api, os.environ.get('GITHUB_TRIGGERING_ACTOR', os.environ['GITHUB_ACTOR'])), 'Rerun actor is not allowed to distribute')
    repository = api.request('')
    require(repository['visibility'] == 'public' and repository['default_branch'] == 'work', 'Requires public repository with work as default')
    pr = api.request(f'/pulls/{pr_number}')
    require(pr['base']['ref'] == 'work' and pr['base']['repo']['full_name'] == api.repo, 'PR must target work')
    require(pr['head']['repo'] and pr['head']['repo']['full_name'] == api.repo, 'Import external contributions into a reviewed local branch first')
    check_checks(api, pr['head']['sha'])
    candidate = None
    if mode == 'test':
        require(pr['state'] == 'open' and not pr['merged'], 'Test requires an open PR')
        sha = pr['head']['sha']
    else:
        require(pr['merged'] and pr['merged_at'], 'Release requires a merged PR')
        comment, tag = approval(api, pr_number, pr['head']['sha'], pr['merged_at'])
        release = api.request('/releases/tags/' + tag)
        candidate = json.loads(api.asset(release, 'pipeline.json'))
        validate_candidate(api, release, candidate, pr, comment)
        sha = pr['merge_commit_sha']
        require(tree(api, sha) == candidate['source_tree'], 'Merge changed tested content; retest an updated PR')
        require(api.request('/git/ref/heads/work')['object']['sha'] == sha, 'PR is no longer the tip of work; prepare a new candidate')
    version = version_at(api, sha)
    code = 1000 + int(os.environ['GITHUB_RUN_NUMBER'])
    require(code <= 2100000000, 'Version code exhausted')
    baseline = json.loads((Path(__file__).resolve().parent.parent / 'distribution-baseline.json').read_text())
    require(code > baseline['version_code'], 'Version code must exceed the pre-pipeline baseline')
    require(version_tuple(version) > version_tuple(baseline['version_name']), 'Version must exceed the pre-pipeline baseline')
    # Include legacy stable releases when checking version names. Unknown stable
    # metadata blocks publication instead of assuming an unsafe baseline.
    releases = api.pages('/releases')
    for release in releases:
        if release['draft'] or release['prerelease']:
            continue
        require(bool(re.fullmatch(r'v[0-9]+\.[0-9]+\.[0-9]+', release['tag_name'])), 'Stable release has an unrecognized version')
        require(version_tuple(version) > version_tuple(release['tag_name'][1:]), 'Version name must increase')
        previous = json.loads(api.asset(release, 'pipeline.json'))
        require(code > previous['version_code'], 'Version code must exceed every published version')
        if mode == 'release':
            comparison = api.request('/compare/' + previous['source_sha'] + '...' + sha)
            require(comparison['status'] == 'ahead', 'Cannot publish older or unrelated code')
    tag = f'teste-pr{pr_number}-r{os.environ["GITHUB_RUN_NUMBER"]}' if mode == 'test' else 'v' + version
    require(not any(r['tag_name'] == tag and not r['draft'] for r in releases), 'Already published; never overwrite a tested APK')
    return {'schema': 1, 'mode': mode, 'pr': pr_number, 'source_sha': sha,
            'source_tree': tree(api, sha), 'version_name': version, 'version_code': code,
            'run_id': int(os.environ['GITHUB_RUN_ID']), 'tag': tag,
            'candidate_tag': candidate['tag'] if candidate else None,
            'candidate_apk_sha256': candidate['apk_sha256'] if candidate else None,
            'change_title': pr.get('title', f'PR #{pr_number}')}


def ensure_tag(api, plan, create=False):
    try:
        ref = api.request('/git/ref/tags/' + plan['tag'])
    except APIError as error:
        if error.status != 404 or not create:
            raise
        ref = api.request('/git/refs', 'POST', {'ref': 'refs/tags/' + plan['tag'], 'sha': plan['source_sha']})
    require(ref['object']['type'] == 'commit' and ref['object']['sha'] == plan['source_sha'],
            'Tag points to different code; never move an existing release tag')


def stage(api, plan, unsigned):
    digest = hashlib.sha256(unsigned.read_bytes()).hexdigest()
    ensure_tag(api, plan, create=True)
    # A retry may replace only its own unpublished transport draft.
    existing = [r for r in api.pages('/releases') if r['tag_name'] == plan['tag']]
    for old in existing:
        require(old['draft'] and old['body'] == f'Transport run {plan["run_id"]}', 'Refusing to replace another release')
        api.request(f'/releases/{old["id"]}', 'DELETE')
    release = api.request('/releases', 'POST', {'tag_name': plan['tag'], 'target_commitish': plan['source_sha'],
        'name': plan['tag'], 'body': f'Transport run {plan["run_id"]}', 'draft': True, 'prerelease': plan['mode'] == 'test'})
    api.upload(release, unsigned)
    return release['id'], digest


def publish(api, plan, release_id, output):
    # Repeat authorization and source/approval checks after build and signing.
    current = resolve(api, plan['mode'], plan['pr'])
    require(current == plan, 'Distribution evidence changed during build')
    release = api.request(f'/releases/{release_id}')
    require(release['draft'] and release['tag_name'] == plan['tag'] and release['body'] == f'Transport run {plan["run_id"]}', 'Draft changed')
    apk = output / 'Meu-Financeiro.apk'
    checksum = hashlib.sha256(apk.read_bytes()).hexdigest()
    plan = dict(plan, apk=apk.name, apk_sha256=checksum)
    metadata = output / 'pipeline.json'
    metadata.write_text(json.dumps(plan, indent=2) + '\n')
    checksums = output / 'SHA256SUMS'
    checksums.write_text(''.join(f'{hashlib.sha256(p.read_bytes()).hexdigest()}  {p.name}\n' for p in (apk, metadata)))
    # Remove the unsigned transfer asset before exposing any release publicly.
    for asset in api.pages(f'/releases/{release_id}/assets'):
        api.request(f'/releases/assets/{asset["id"]}', 'DELETE')
    for path in (apk, metadata, checksums):
        api.upload(release, path)
        require(api.asset(release, path.name) == path.read_bytes(), 'Uploaded asset readback failed')
    notes = (f'{plan["change_title"]}\n\nPR #{plan["pr"]}\n\nCommit: `{plan["source_sha"]}`\n\n'
             f'Versão: {plan["version_name"]} · versionCode: {plan["version_code"]}\n\n'
             f'APK SHA-256: `{checksum}`\n\n')
    if plan['mode'] == 'test':
        notes += ('Instale **Meu Financeiro Teste**, separado do principal. Teste a feature descrita no PR, '
                  'desbloqueio, reabertura e persistência com dados de teste.\n\n'
                  'Depois de testar, comente no PR exatamente:\n\n'
                  f'```\nAprovado para publicação: {plan["tag"]} {plan["source_sha"]}\n```\n')
    else:
        notes += f'Candidato aprovado: `{plan["candidate_tag"]}` (APK SHA-256 `{plan["candidate_apk_sha256"]}`). Instale sobre o aplicativo principal, sem desinstalar.\n'
    notes += f'\nMudanças e roteiro específico: https://github.com/{api.repo}/pull/{plan["pr"]}\n'
    require(resolve(api, plan['mode'], plan['pr']) == current, 'Evidence changed before publication')
    ensure_tag(api, plan)
    result = api.request(f'/releases/{release_id}', 'PATCH', {'body': notes, 'draft': False,
        'make_latest': 'false' if plan['mode'] == 'test' else 'true'})
    require(not result['draft'], 'Release did not publish')
    print(result['html_url'])
    if os.environ.get('GITHUB_STEP_SUMMARY'):
        with open(os.environ['GITHUB_STEP_SUMMARY'], 'a') as summary:
            summary.write(f'## Download\n\n[{plan["tag"]}]({result["html_url"]})\n\n{notes}')
    # Only remove pipeline-owned prereleases for this PR, never stable versions.
    candidates = []
    for r in api.pages('/releases'):
        if not r['draft'] and r['prerelease'] and re.fullmatch(f'teste-pr{plan["pr"]}-r[0-9]+', r['tag_name']):
            candidates.append(r)
    candidates.sort(key=lambda r: r['id'], reverse=True)
    for r in (candidates[3:] if plan['mode'] == 'test' else candidates):
        api.request(f'/releases/{r["id"]}', 'DELETE')
        api.request('/git/refs/tags/' + r['tag_name'], 'DELETE')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('command', choices=['resolve', 'stage', 'download', 'publish'])
    parser.add_argument('--mode', choices=['test', 'release'])
    parser.add_argument('--pr', type=int)
    parser.add_argument('--plan', default='plan.json')
    parser.add_argument('--file', default='unsigned.apk')
    parser.add_argument('--release-id', type=int)
    parser.add_argument('--sha256')
    parser.add_argument('--output', default='distribution')
    args = parser.parse_args()
    api = GitHub()
    if args.command == 'resolve':
        require(args.pr and args.pr > 0 and args.mode, 'Mode and positive PR number required')
        plan = resolve(api, args.mode, args.pr)
        Path(args.plan).write_text(json.dumps(plan))
        with open(os.environ['GITHUB_OUTPUT'], 'a') as output:
            output.write('plan=' + json.dumps(plan, separators=(',', ':')) + '\n')
            output.write('sha=' + plan['source_sha'] + '\n')
    else:
        plan = json.loads(Path(args.plan).read_text())
        if args.command == 'stage':
            release_id, checksum = stage(api, plan, Path(args.file))
            with open(os.environ['GITHUB_OUTPUT'], 'a') as output:
                output.write(f'release_id={release_id}\nsha256={checksum}\n')
        elif args.command == 'download':
            require(resolve(api, plan['mode'], plan['pr']) == plan, 'Plan changed')
            release = api.request(f'/releases/{args.release_id}')
            require(release['draft'] and release['tag_name'] == plan['tag'] and release['body'] == f'Transport run {plan["run_id"]}', 'Wrong transfer draft')
            data = api.asset(release, 'unsigned.apk')
            require(hashlib.sha256(data).hexdigest() == args.sha256, 'Unsigned transfer checksum mismatch')
            Path(args.file).write_bytes(data)
        else:
            publish(api, plan, args.release_id, Path(args.output))


if __name__ == '__main__':
    try:
        main()
    except (Blocked, KeyError, ValueError) as error:
        raise SystemExit(f'Distribution blocked: {error}')
