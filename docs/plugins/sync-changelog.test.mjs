import assert from 'node:assert/strict';
import {spawnSync} from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';
import {fileURLToPath} from 'node:url';

const script = fileURLToPath(new URL('../scripts/sync-changelog.mjs', import.meta.url));
const website = 'https://kotlin.github.io/Library-API-Watchdog/';

test('only a website link to a docs page becomes a link to the page file', (t) => {
  const page = websiteChangelog(t, [
    `- [API filters](${website}exemptions#api-filters)`,
    `- [Redundant exemptions](${website}checks/special/redundant-exemption)`,
    `- [API reference](${website}api/)`,
    `- [API reference](${website}api)`,
    `- [Java interop](${website}checks/java-interop/)`,
    `- [WatchdogChecks](${website}api/gradle-plugin/kotlinx.library.watchdog/-watchdog-checks/index.html#entries)`,
    `- [Report](${website}img/report.png)`,
    `- [Website](${website})`,
  ]);
  assert.deepEqual(page.split('\n').slice(-9, -1), [
    '- [API filters](exemptions.md#api-filters)',
    '- [Redundant exemptions](checks/special/redundant-exemption.md)',
    `- [API reference](${website}api/)`,
    `- [API reference](${website}api)`,
    `- [Java interop](${website}checks/java-interop/)`,
    `- [WatchdogChecks](${website}api/gradle-plugin/kotlinx.library.watchdog/-watchdog-checks/index.html#entries)`,
    `- [Report](${website}img/report.png)`,
    `- [Website](${website})`,
  ]);
});

function websiteChangelog(t, entries) {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'watchdog-changelog-'));
  t.after(() => fs.rmSync(directory, {recursive: true, force: true}));
  const copy = path.join(directory, 'docs/scripts/sync-changelog.mjs');
  fs.mkdirSync(path.dirname(copy), {recursive: true});
  fs.mkdirSync(path.join(directory, 'docs/pages'));
  fs.copyFileSync(script, copy);
  fs.writeFileSync(path.join(directory, 'diagnostics.json'), JSON.stringify({diagnostics: [{name: 'SAMPLE', docs: 'checks/sample'}]}));
  fs.writeFileSync(path.join(directory, 'CHANGELOG.md'), ['# Changelog', '', ...entries, ''].join('\n'));
  const run = spawnSync(process.execPath, [copy], {encoding: 'utf8'});
  assert.equal(run.status, 0, run.stderr);
  return fs.readFileSync(path.join(directory, 'docs/pages/changelog.md'), 'utf8');
}
