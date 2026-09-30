// The work-item tracker, as the bug cycle and the story reader use it. This file talks to Azure DevOps with the
// credential git already has for it (`git credential fill` - a PAT with Work Items Read & Write; never printed).
// Settings: bugcycle/config.json -> tracker {org, project}.
//
// Another tracker (Jira, GitHub Issues ...): keep this file's exports - wiql / items / patch / link / comments /
// download - and implement them for that tracker; watch.js, move.js and testingActivity/story.js only use those.
const {execSync} = require('child_process');
const {config} = require('./lib');

const tracker = config.tracker || {};
// e.g. https://dev.azure.com/<organisation>  or  https://<organisation>.visualstudio.com/DefaultCollection
const org = String(tracker.org || '').replace(/\/+$/, '');
const project = tracker.project;
let auth = null;

function authHeader() {
    if (!org || !project || /[<>]/.test(org + project)) {
        throw new Error('bugcycle/config.json -> tracker.org / tracker.project are not set');
    }
    if (!auth) {
        const host = new URL(org).host;
        const c = execSync('git credential fill', {input: 'protocol=https\nhost=' + host + '\n\n', encoding: 'utf8'});
        const user = /username=(.*)/.exec(c);
        const pass = /password=(.*)/.exec(c);
        if (!pass) throw new Error('git has no saved credential for ' + host);
        auth = 'Basic ' + Buffer.from((user ? user[1].trim() : '') + ':' + pass[1].trim()).toString('base64');
    }
    return auth;
}

async function call(method, url, body, type) {
    const r = await fetch(url, {method, headers: {Authorization: authHeader(), 'Content-Type': type || 'application/json'},
        body: body ? JSON.stringify(body) : undefined});
    const j = await r.json().catch(() => ({}));
    if (!r.ok) throw new Error(method + ' ' + url.replace(org, '') + ' -> HTTP ' + r.status + (j.message ? ': ' + j.message : ''));
    return j;
}

/** Ids of the work items a WIQL query returns (at most {top} when given). */
async function wiql(query, top) {
    const j = await call('POST', `${org}/${project}/_apis/wit/wiql?api-version=7.1${top ? '&$top=' + top : ''}`, {query});
    return (j.workItems || []).map(w => w.id);
}

/** Work items with their fields (and relations). */
async function items(ids) {
    if (!ids.length) return [];
    const j = await call('GET', `${org}/${project}/_apis/wit/workitems?ids=${ids.join(',')}&$expand=relations&api-version=7.1`);
    return j.value || [];
}

/** JSON-patch a work item; validateOnly checks the change without saving it. */
async function patch(id, ops, validateOnly) {
    return call('PATCH', `${org}/${project}/_apis/wit/workitems/${id}?api-version=7.1${validateOnly ? '&validateOnly=true' : ''}`,
        ops, 'application/json-patch+json');
}

const link = id => `${String(tracker.webUrl || org).replace(/\/+$/, '')}/${project}/_workitems/edit/${id}`;

/** The discussion of a work item, oldest first. */
async function comments(id) {
    const j = await call('GET', `${org}/${project}/_apis/wit/workItems/${id}/comments?order=asc&api-version=7.1-preview.4`);
    return j.comments || [];
}

/** An attachment's bytes (url from a relation of type AttachedFile). */
async function download(url) {
    const r = await fetch(url, {headers: {Authorization: authHeader()}});
    if (!r.ok) throw new Error('GET attachment -> HTTP ' + r.status);
    return Buffer.from(await r.arrayBuffer());
}

module.exports = {wiql, items, patch, link, comments, download};
