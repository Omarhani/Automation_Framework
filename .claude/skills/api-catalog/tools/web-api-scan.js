#!/usr/bin/env node
/*
 * Reads a web app's own script bundle and lists every API endpoint it knows, with every place that calls it and the
 * request body written there. Use it to build and refresh the api-catalog skill of a project.
 *
 *   node .claude/skills/api-catalog/tools/web-api-scan.js <web app url> [work folder]
 *
 *   web app url  the app's base URL on a TEST environment. Only static files are fetched - no login, no API call.
 *   work folder  default <os temp>/web-api-scan. Gets:
 *                  chunks/            the app's main script and every lazy chunk
 *                  endpoints.json     [constant name, "Controller/Action"] pairs found in the main script
 *                  endpoints.md       the same, grouped by controller (compare with the catalog's endpoints file)
 *                  calls/<Controller>.md   per endpoint: [chunk] enclosingMethod(args) → request(<const>, <body>)
 *                  no-call-site.txt   endpoints the app never calls (constants only - their bodies are unknown)
 *
 * What it understands: a webpack / Angular-CLI style build (index page → runtime.<hash>.js with the chunk map,
 * main.<hash>.js) that keeps its endpoints as "Name/Action" string constants and calls them through request helpers
 * (postRequest(endpoint, body), get(...), ...). An app built another way gives few or no endpoints here - then read
 * the calls from the browser's network tab or the Timeline tab of a test report instead, and adjust the two
 * patterns marked PATTERN below.
 *
 * Reading a body: the call site shows the body as the app writes it. When it is a variable or `someForm.value`,
 * open the chunk and follow it:
 *   grep -o '.\{400\}<text from the call>.\{600\}' chunks/<id>.*.js | cut -c1-2000
 * Wrapper helpers (a base-request method that adds paging / language fields, an HTTP interceptor) change what is
 * really sent: find them once in main.js and note them at the top of the catalog.
 */
const fs = require('fs');
const os = require('os');
const path = require('path');

if (!process.argv[2]) {
  console.error('usage: node web-api-scan.js <web app url> [work folder]');
  process.exit(2);
}
const base = process.argv[2].replace(/\/+$/, '');
const work = process.argv[3] || path.join(os.tmpdir(), 'web-api-scan');
const chunksDir = path.join(work, 'chunks');

async function text(url) {
  const response = await fetch(url);
  if (!response.ok) throw new Error(url + ' answered HTTP ' + response.status);
  return response.text();
}

async function download() {
  fs.mkdirSync(chunksDir, { recursive: true });
  const index = await text(base + '/');
  const script = name => {
    const found = index.match(new RegExp('src="(' + name + '\\.[0-9a-f]+\\.js)"'));
    if (!found) throw new Error('No ' + name + '.<hash>.js in the index page of ' + base + ' - not a build this script understands');
    return found[1];
  };
  const runtime = await text(base + '/' + script('runtime'));
  fs.writeFileSync(path.join(chunksDir, 'main.js'), await text(base + '/' + script('main')));

  // runtime: f.u = e => (<id> === e ? "common" : e) + "." + {155:"<hash>", ...}[e] + ".js"
  const map = (runtime.match(/\{(\d+:"[0-9a-f]{16}",?)+\}/g) || []).sort((a, b) => b.length - a.length)[0];
  if (!map) throw new Error('No chunk map in the runtime script');
  const named = {};
  for (const m of runtime.matchAll(/(\d+)===e\?"([\w-]+)"/g)) named[m[1]] = m[2];
  const files = [...map.matchAll(/(\d+):"([0-9a-f]{16})"/g)].map(m => (named[m[1]] || m[1]) + '.' + m[2] + '.js');

  let next = 0;
  await Promise.all(Array.from({ length: 8 }, async () => {
    while (next < files.length) {
      const file = files[next++];
      const body = await text(base + '/' + file);
      // a missing chunk comes back as the index page
      if (!body.startsWith('<!doctype') && !body.startsWith('<!DOCTYPE')) fs.writeFileSync(path.join(chunksDir, file), body);
    }
  }));
  return files.length + 1;
}

/** The call's argument list, from its "(" to the matching ")". */
function balanced(source, open) {
  let depth = 0, quote = null;
  for (let i = open; i < source.length && i < open + 6000; i++) {
    const c = source[i];
    if (quote) {
      if (c === '\\') { i++; continue; }
      if (c === quote) quote = null;
      continue;
    }
    if (c === '"' || c === "'" || c === '`') { quote = c; continue; }
    if (c === '(' || c === '{' || c === '[') depth++;
    else if (c === ')' || c === '}' || c === ']') { depth--; if (depth === 0) return source.slice(open, i + 1); }
  }
  return source.slice(open, open + 3000) + '…';
}

function extract() {
  const main = fs.readFileSync(path.join(chunksDir, 'main.js'), 'utf8');
  // PATTERN 1: endpoint constants  name:"Controller/Action"
  const endpoints = [...main.matchAll(/([A-Za-z_0-9$]+):"([A-Za-z0-9_]+\/[A-Za-z0-9_\/]*)"/g)]
    .map(m => [m[1], m[2]])
    .filter(([, endpoint]) => !endpoint.startsWith('application/'));
  fs.writeFileSync(path.join(work, 'endpoints.json'), JSON.stringify(endpoints));

  const byController = {};
  for (const [, endpoint] of endpoints) {
    const [controller, ...action] = endpoint.split('/');
    (byController[controller] = byController[controller] || []).push(action.join('/'));
  }
  fs.writeFileSync(path.join(work, 'endpoints.md'), Object.keys(byController).sort((a, b) => a.localeCompare(b))
    .map(c => '- **' + c + '** (' + byController[c].length + '): ' + byController[c].join(', ')).join('\n') + '\n');

  const byKey = {};
  for (const [key, endpoint] of endpoints) (byKey[key] = byKey[key] || []).push(endpoint);
  const calls = {};
  for (const file of fs.readdirSync(chunksDir).filter(f => f.endsWith('.js'))) {
    const source = fs.readFileSync(path.join(chunksDir, file), 'utf8');
    // PATTERN 2: the request helpers the app calls its API through
    const callRe = /\b(\w*[Rr]equest\w*|post|get|put|patch|delete)\(/g;
    let m;
    while ((m = callRe.exec(source))) {
      const open = m.index + m[0].length - 1;
      const head = source.slice(open, open + 160);
      const constant = head.match(/^\(\s*`?\$?\{?\s*[\w$]+\.[\w$]+\.([A-Za-z_$][\w$]*)/);
      const literal = head.match(/^\(\s*["'`]([A-Za-z]+\/[A-Za-z0-9_\/]+)/);
      const endpoint = constant && byKey[constant[1]] ? byKey[constant[1]].join('|') : literal ? literal[1] + ' (literal)' : null;
      if (!endpoint) continue;
      const before = source.slice(Math.max(0, open - 1500), open);
      const method = [...before.matchAll(/([A-Za-z_$][\w$]*)\(([\w$,={}\[\]"'!.\s]*)\)\{/g)]
        .filter(x => !['if', 'for', 'while', 'switch', 'catch', 'function'].includes(x[1])).pop();
      const call = balanced(source, open);
      (calls[endpoint] = calls[endpoint] || []).push({
        chunk: file.split('.')[0],
        method: method ? method[1] + '(' + method[2] + ')' : '?',
        call: m[1] + (call.length > 2500 ? call.slice(0, 2500) + '…' : call),
      });
    }
  }

  const callsDir = path.join(work, 'calls');
  fs.rmSync(callsDir, { recursive: true, force: true });
  fs.mkdirSync(callsDir, { recursive: true });
  const pages = {};
  for (const endpoint of Object.keys(calls).sort()) {
    const controller = endpoint.split('/')[0];
    // two controllers that differ only by case: keep them apart on a case-insensitive disk
    const page = controller === controller.toLowerCase() ? controller + '_lower' : controller;
    const seen = new Set();
    let block = '\n### ' + endpoint + '\n';
    for (const site of calls[endpoint]) {
      const signature = site.call.replace(/\s+/g, '');
      if (seen.has(signature)) continue;
      seen.add(signature);
      block += '- [' + site.chunk + '] ' + site.method + ' → ' + site.call + '\n';
    }
    pages[page] = (pages[page] || '') + block;
  }
  for (const page of Object.keys(pages)) fs.writeFileSync(path.join(callsDir, page + '.md'), pages[page]);

  const called = new Set(Object.keys(calls).flatMap(e => e.replace(' (literal)', '').split('|')));
  const unused = endpoints.map(e => e[1]).filter(e => !called.has(e));
  fs.writeFileSync(path.join(work, 'no-call-site.txt'), unused.join('\n') + '\n');
  return { endpoints: endpoints.length, controllers: Object.keys(byController).length, withCalls: called.size, unused: unused.length };
}

(async () => {
  const files = await download();
  const result = extract();
  console.log(base + ': ' + files + ' script files → ' + work);
  console.log(result.endpoints + ' endpoints in ' + result.controllers + ' controllers; ' + result.withCalls
    + ' have a call site (calls/), ' + result.unused + ' are constants only (no-call-site.txt)');
})().catch(error => {
  console.error(error.message);
  process.exit(1);
});
