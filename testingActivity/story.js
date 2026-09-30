// Reads a user story from the tracker (Azure DevOps through bugcycle/ado.js) with everything around it, for the Testing Activity agent
// (.claude/agents/testing-activity.md): the story, its discussion and attachments, parent / children / related items,
// siblings under the same parent, recent stories and bugs of the same area, and keyword matches.
//
//   node testingActivity/story.js <storyId> [--keywords "order,refund"] [--recent 30] [--no-attachments]
//
// Writes testingActivity/stories/<id>/story.md (+ attachments/, gitignored) and prints the path.
// Read only: never changes a work item. Auth = the git credential (bugcycle/ado.js), never printed.
const fs = require('fs');
const path = require('path');
const ado = require('../bugcycle/ado');
const {args, config} = require('../bugcycle/lib');

// the work item types that count as "stories and bugs" when looking for history (tracker.storyTypes)
const types = ((config.tracker || {}).storyTypes || ['User Story', 'Bug']).map(t => `'${t}'`).join(', ');

const a = args(process.argv.slice(2));
const id = Number(process.argv[2]);
if (!id) {
    console.error('usage: node testingActivity/story.js <storyId> [--keywords "a,b"] [--recent 30] [--no-attachments]');
    process.exit(2);
}
const recent = Number(a.recent) || 30;
const dir = path.join(__dirname, 'stories', String(id));

/** Azure rich text (HTML) -> readable plain text with list dashes and line breaks. */
function text(html) {
    if (!html) return '';
    return String(html)
        .replace(/<li[^>]*>/gi, '\n- ').replace(/<(br|\/p|\/div|\/h\d|\/tr)[^>]*>/gi, '\n')
        .replace(/<img[^>]*src="([^"]+)"[^>]*>/gi, ' [image: $1] ')
        .replace(/<[^>]+>/g, '')
        .replace(/&nbsp;/g, ' ').replace(/&amp;/g, '&').replace(/&lt;/g, '<').replace(/&gt;/g, '>')
        .replace(/&quot;/g, '"').replace(/&#39;/g, "'")
        .replace(/[ \t]+\n/g, '\n').replace(/\n- *\n+/g, '\n- ').replace(/\n{3,}/g, '\n\n').trim();
}

const idOf = rel => Number(rel.url.split('/').pop());
const f = (w, k) => w.fields[k];
const who = v => (v && (v.displayName || v)) || '-';
const row = w => `| [${w.id}](${ado.link(w.id)}) | ${f(w, 'System.WorkItemType')} | ${f(w, 'System.State')} | ${String(f(w, 'System.Title')).replace(/\|/g, '/')} |`;
const table = list => list.length ? '| Id | Type | State | Title |\n|---|---|---|---|\n' + list.map(row).join('\n') : '_none_';

function keywordsOf(title) {
    const stop = new Set(['with', 'from', 'that', 'this', 'when', 'should', 'able', 'user', 'admin', 'page', 'screen', 'the', 'and', 'for']);
    return title.split(/[^\p{L}\p{N}]+/u).filter(w => w.length >= 4 && !stop.has(w.toLowerCase())).slice(0, 3);
}

/** Full item with its description, acceptance criteria and repro steps as text. */
function details(w) {
    const parts = [];
    for (const [k, label] of [['System.Description', 'Description'], ['Microsoft.VSTS.Common.AcceptanceCriteria', 'Acceptance criteria'],
        ['Microsoft.VSTS.TCM.ReproSteps', 'Repro steps'], ['Microsoft.VSTS.TCM.SystemInfo', 'System info']]) {
        const t = text(f(w, k));
        if (t) parts.push(`**${label}**\n\n${t}`);
    }
    return parts.join('\n\n');
}

(async () => {
    fs.mkdirSync(dir, {recursive: true});
    const [story] = await ado.items([id]);
    if (!story) throw new Error('work item ' + id + ' not found');
    const rels = story.relations || [];
    const byRel = r => rels.filter(x => x.rel === r).map(idOf);
    const parentIds = byRel('System.LinkTypes.Hierarchy-Reverse');
    const childIds = byRel('System.LinkTypes.Hierarchy-Forward');
    const relatedIds = rels.filter(x => /Related|Dependency|Duplicate|Tested/.test(x.rel)).map(idOf);

    const out = [];
    out.push(`# ${f(story, 'System.WorkItemType')} ${id} - ${f(story, 'System.Title')}`, '',
        `[Open in the tracker](${ado.link(id)}) · state **${f(story, 'System.State')}** · area \`${f(story, 'System.AreaPath')}\` · iteration \`${f(story, 'System.IterationPath')}\``,
        `Assigned: ${who(f(story, 'System.AssignedTo'))} · created by ${who(f(story, 'System.CreatedBy'))} ${String(f(story, 'System.CreatedDate')).slice(0, 10)} · changed ${String(f(story, 'System.ChangedDate')).slice(0, 10)}`,
        '', details(story) || '_no description / acceptance criteria_');

    // Discussion
    const comments = await ado.comments(id).catch(e => (console.error('comments: ' + e.message), []));
    out.push('', '## Discussion', '', comments.length
        ? comments.map(c => `- **${who(c.createdBy)}** ${String(c.createdDate).slice(0, 10)}: ${text(c.text).replace(/\n+/g, ' ')}`).join('\n')
        : '_none_');

    // Attachments (downloaded unless --no-attachments; pictures are gitignored)
    const files = rels.filter(x => x.rel === 'AttachedFile');
    const saved = [];
    if (files.length && !a['no-attachments']) {
        fs.mkdirSync(path.join(dir, 'attachments'), {recursive: true});
        for (const x of files) {
            const name = (x.attributes && x.attributes.name) || idOf(x) + '.bin';
            try {
                fs.writeFileSync(path.join(dir, 'attachments', name), await ado.download(x.url));
                saved.push(`attachments/${name}`);
            } catch (e) { saved.push(`${name} (not downloaded: ${e.message})`); }
        }
    }
    out.push('', '## Attachments', '', files.length ? (saved.length ? saved : files.map(x => x.attributes && x.attributes.name)).map(s => '- ' + s).join('\n') : '_none_');

    // Parent, siblings, children, related
    const parents = await ado.items(parentIds);
    let siblings = [];
    if (parents.length) {
        const sibIds = (parents[0].relations || []).filter(x => x.rel === 'System.LinkTypes.Hierarchy-Forward').map(idOf).filter(x => x !== id);
        siblings = await ado.items(sibIds.slice(0, 60));
    }
    const children = await ado.items(childIds);
    const related = await ado.items(relatedIds);
    out.push('', '## Parent', '', parents.length ? parents.map(p => row(p)).join('\n').replace(/^/, '| Id | Type | State | Title |\n|---|---|---|---|\n') : '_none_');
    if (parents.length && details(parents[0])) out.push('', details(parents[0]));
    out.push('', '## Children (tasks, bugs, test cases)', '', table(children));
    const childBugs = children.filter(c => f(c, 'System.WorkItemType') === ((config.tracker || {}).bugType || 'Bug'));
    for (const b of childBugs) out.push('', `### Bug ${b.id} - ${f(b, 'System.Title')} (${f(b, 'System.State')})`, '', details(b));
    out.push('', '## Related links', '', table(related));
    out.push('', '## Siblings (same parent)', '', table(siblings));

    // Same area, recent
    const seen = new Set([id, ...parentIds, ...childIds, ...relatedIds, ...siblings.map(s => s.id)]);
    const area = f(story, 'System.AreaPath').replace(/'/g, "''");
    const recentIds = (await ado.wiql(`SELECT [System.Id] FROM WorkItems WHERE [System.TeamProject] = @project
        AND [System.AreaPath] UNDER '${area}' AND [System.WorkItemType] IN (${types})
        ORDER BY [System.ChangedDate] DESC`, recent + seen.size)).filter(x => !seen.has(x)).slice(0, recent);
    recentIds.forEach(x => seen.add(x));
    out.push('', `## Recent stories and bugs in \`${f(story, 'System.AreaPath')}\``, '', table(await ado.items(recentIds)));

    // Keyword matches
    const words = a.keywords ? String(a.keywords).split(',').map(s => s.trim()).filter(Boolean) : keywordsOf(f(story, 'System.Title'));
    for (const w of words) {
        const ids = (await ado.wiql(`SELECT [System.Id] FROM WorkItems WHERE [System.TeamProject] = @project
            AND [System.Title] CONTAINS '${w.replace(/'/g, "''")}' AND [System.WorkItemType] IN (${types})
            ORDER BY [System.ChangedDate] DESC`, 40)).filter(x => !seen.has(x)).slice(0, 20);
        ids.forEach(x => seen.add(x));
        out.push('', `## Keyword "${w}"`, '', table(await ado.items(ids)));
    }
    out.push('', `_Read ${new Date().toISOString().slice(0, 16).replace('T', ' ')} UTC by testingActivity/story.js. Open any id above with \`node testingActivity/story.js <id> --no-attachments\` for its full text._`);

    const file = path.join(dir, 'story.md');
    fs.writeFileSync(file, out.join('\n') + '\n');
    console.log(path.relative(process.cwd(), file));
})().catch(e => { console.error(e.message); process.exit(1); });
