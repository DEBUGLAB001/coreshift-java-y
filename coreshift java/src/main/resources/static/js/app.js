// CoreShift Dashboard Logic
// Relative API paths for preview proxy compatibility
const API_BASE = 'api';

let state = {
    jobs: [],
    workers: [],
    queue: [],
    readinessMap: {},
    graph: { nodes: [], edges: [] },
    events: [],
    filter: 'ALL'
};

// DOM References
const el = {
    totalJobs: document.getElementById('metric-total-jobs'),
    jobsBreakdown: document.getElementById('metric-jobs-breakdown'),
    queueLength: document.getElementById('metric-queue-length'),
    readyJobs: document.getElementById('metric-ready-jobs'),
    clusterCpu: document.getElementById('metric-cluster-cpu'),
    clusterMem: document.getElementById('metric-cluster-mem'),
    progressCpu: document.getElementById('progress-cluster-cpu'),
    progressMem: document.getElementById('progress-cluster-mem'),
    workersContainer: document.getElementById('workers-container'),
    queueTableBody: document.getElementById('queue-table-body'),
    queueCountTag: document.getElementById('queue-count-tag'),
    jobsTableBody: document.getElementById('jobs-table-body'),
    eventsContainer: document.getElementById('events-container'),
    dagCanvas: document.getElementById('dag-canvas'),
    toastBanner: document.getElementById('toast-banner'),
    toastMessage: document.getElementById('toast-message'),
    btnCloseToast: document.getElementById('btn-close-toast'),
    btnResetScenario: document.getElementById('btn-reset-scenario'),
    btnRunCycle: document.getElementById('btn-run-cycle'),
    btnOpenSubmitModal: document.getElementById('btn-open-submit-modal'),
    submitJobModal: document.getElementById('submit-job-modal'),
    btnCloseSubmitModal: document.getElementById('btn-close-submit-modal'),
    btnCancelSubmit: document.getElementById('btn-cancel-submit'),
    submitJobForm: document.getElementById('submit-job-form'),
    inputJobDeps: document.getElementById('input-job-deps'),
    reassignModal: document.getElementById('reassign-modal'),
    btnCloseReassignModal: document.getElementById('btn-close-reassign-modal'),
    btnCancelReassign: document.getElementById('btn-cancel-reassign'),
    reassignForm: document.getElementById('reassign-form'),
    reassignJobId: document.getElementById('reassign-job-id'),
    reassignJobDesc: document.getElementById('reassign-job-desc'),
    selectTargetWorker: document.getElementById('select-target-worker')
};

// Toast notification helper
function showToast(message, type = 'info') {
    el.toastMessage.textContent = message;
    el.toastBanner.className = `toast-banner ${type}`;
    setTimeout(() => {
        el.toastBanner.classList.add('hidden');
    }, 5000);
}

el.btnCloseToast.addEventListener('click', () => {
    el.toastBanner.classList.add('hidden');
});

// Set loading state on a button
function setButtonLoading(button, isLoading, text) {
    if (isLoading) {
        button.disabled = true;
        button.dataset.originalText = button.innerHTML;
        button.innerHTML = `<span class="spinner"></span> <span>${text || 'Processing...'}</span>`;
    } else {
        button.disabled = false;
        button.innerHTML = button.dataset.originalText || button.innerHTML;
    }
}

// Data Fetching & Sync
async function fetchAllData() {
    try {
        const [jobsRes, workersRes, queueRes, graphRes, eventsRes] = await Promise.all([
            fetch(`${API_BASE}/jobs`),
            fetch(`${API_BASE}/workers`),
            fetch(`${API_BASE}/queue`),
            fetch(`${API_BASE}/graph`),
            fetch(`${API_BASE}/events`)
        ]);

        if (jobsRes.ok) state.jobs = await jobsRes.json();
        if (workersRes.ok) state.workers = await workersRes.json();
        if (queueRes.ok) {
            const qData = await queueRes.json();
            state.queue = qData.queue || [];
            state.readinessMap = qData.readinessMap || {};
        }
        if (graphRes.ok) state.graph = await graphRes.json();
        if (eventsRes.ok) state.events = await eventsRes.json();

        renderAll();
    } catch (err) {
        console.error('Failed to sync cluster state:', err);
    }
}

// Render Operations
function renderAll() {
    renderMetrics();
    renderWorkers();
    renderQueueTable();
    renderJobsTable();
    renderEvents();
    renderDag();
    populateDependenciesSelect();
}

function renderMetrics() {
    const total = state.jobs.length;
    const running = state.jobs.filter(j => j.status === 'RUNNING').length;
    const pending = state.jobs.filter(j => j.status === 'PENDING').length;
    const readyCount = state.queue.length;

    el.totalJobs.textContent = total;
    el.jobsBreakdown.textContent = `${running} running, ${pending} pending`;
    el.queueLength.textContent = readyCount;
    el.readyJobs.textContent = `${readyCount} eligible for dispatch`;

    let totalCpu = 0, usedCpu = 0, totalMem = 0, usedMem = 0;
    state.workers.forEach(w => {
        totalCpu += w.totalCpuCores;
        usedCpu += w.usedCpuCores;
        totalMem += w.totalMemoryMb;
        usedMem += w.usedMemoryMb;
    });

    const cpuPct = totalCpu > 0 ? Math.round((usedCpu / totalCpu) * 100) : 0;
    const memPct = totalMem > 0 ? Math.round((usedMem / totalMem) * 100) : 0;

    el.clusterCpu.textContent = `${cpuPct}%`;
    el.progressCpu.style.width = `${Math.min(100, cpuPct)}%`;

    el.clusterMem.textContent = `${memPct}%`;
    el.progressMem.style.width = `${Math.min(100, memPct)}%`;
}

function renderWorkers() {
    el.workersContainer.innerHTML = '';
    state.workers.forEach(w => {
        const cpuPct = w.cpuUtilizationPercentage;
        const memPct = w.memoryUtilizationPercentage;
        const isCongested = cpuPct >= 70 || memPct >= 70;

        const card = document.createElement('div');
        card.className = 'worker-card';
        card.innerHTML = `
            <div class="worker-header">
                <div>
                    <div class="worker-name">${escapeHtml(w.name)}</div>
                    <div class="worker-id">${escapeHtml(w.id)}</div>
                </div>
                <span class="worker-status ${isCongested ? 'congested' : ''}">${w.status} (${Math.round(cpuPct)}% load)</span>
            </div>
            <div class="worker-stats">
                <div class="stat-item">
                    <div class="stat-row">
                        <span>CPU Cores</span>
                        <span class="stat-val">${w.usedCpuCores} / ${w.totalCpuCores} cores</span>
                    </div>
                    <div class="metric-progress-bar">
                        <div class="metric-progress-fill" style="width: ${Math.min(100, cpuPct)}%"></div>
                    </div>
                </div>
                <div class="stat-item">
                    <div class="stat-row">
                        <span>Memory</span>
                        <span class="stat-val">${w.usedMemoryMb} / ${w.totalMemoryMb} MB</span>
                    </div>
                    <div class="metric-progress-bar">
                        <div class="metric-progress-fill mem-fill" style="width: ${Math.min(100, memPct)}%"></div>
                    </div>
                </div>
            </div>
            <div>
                <span class="stat-row" style="font-size: 0.75rem; color: var(--text-muted);">Active Workloads (${w.activeJobIds.length}):</span>
                <div class="active-jobs-list">
                    ${w.activeJobIds.length > 0
                        ? w.activeJobIds.map(jid => `<span class="job-chip">${escapeHtml(jid)}</span>`).join('')
                        : '<span style="font-size:0.75rem; color:var(--text-muted);">Idle — No workloads</span>'}
                </div>
            </div>
        `;
        el.workersContainer.appendChild(card);
    });
}

function renderQueueTable() {
    el.queueTableBody.innerHTML = '';
    el.queueCountTag.textContent = `${state.queue.length} Ready Jobs`;

    if (state.queue.length === 0) {
        el.queueTableBody.innerHTML = `<tr><td colspan="6" class="text-center" style="color:var(--text-muted); padding: 1.5rem;">Queue is empty. No ready jobs to dispatch.</td></tr>`;
        return;
    }

    state.queue.forEach((item, idx) => {
        const job = item.job;
        const tr = document.createElement('tr');
        tr.innerHTML = `
            <td style="font-family: var(--font-mono); font-weight: 700; color: var(--accent-cyan);">#${idx + 1}</td>
            <td>
                <div style="font-weight: 600;">${escapeHtml(job.name)}</div>
                <div style="font-size: 0.75rem; color: var(--text-muted); font-family: var(--font-mono);">${escapeHtml(job.id)}</div>
            </td>
            <td><span class="status-pill pill-${job.status.toLowerCase()}">${job.status}</span></td>
            <td style="font-family: var(--font-mono);">${job.priority}</td>
            <td style="font-family: var(--font-mono); font-weight: 700; color: var(--accent-indigo);">
                ${item.effectivePriority}
                ${job.retryCount > 0 ? `<span style="font-size:0.7rem; color:var(--text-muted);"> (retry ${job.retryCount})</span>` : ''}
            </td>
            <td style="font-family: var(--font-mono); font-size: 0.8rem;">
                ${job.resourceRequirements ? `${job.resourceRequirements.cpuCores}c / ${job.resourceRequirements.memoryMb}M` : '-'}
            </td>
        `;
        el.queueTableBody.appendChild(tr);
    });
}

function renderJobsTable() {
    el.jobsTableBody.innerHTML = '';
    const filteredJobs = state.jobs.filter(j => {
        if (state.filter === 'ALL') return true;
        return j.status === state.filter;
    });

    if (filteredJobs.length === 0) {
        el.jobsTableBody.innerHTML = `<tr><td colspan="9" class="text-center" style="color:var(--text-muted); padding: 2rem;">No jobs found matching filter '${state.filter}'.</td></tr>`;
        return;
    }

    filteredJobs.forEach(job => {
        const tr = document.createElement('tr');
        const depsStr = job.dependencies && job.dependencies.length > 0
            ? job.dependencies.map(d => `<span class="job-chip">${escapeHtml(d)}</span>`).join(' ')
            : '<span style="color:var(--text-muted); font-size:0.75rem;">None</span>';

        let actionButtons = '';
        if (job.status === 'RUNNING') {
            actionButtons = `
                <button class="btn btn-sm btn-success" onclick="completeJob('${job.id}')" title="Simulate successful execution">Complete</button>
                <button class="btn btn-sm btn-danger" onclick="failJob('${job.id}')" title="Simulate task failure">Fail</button>
                <button class="btn btn-sm btn-secondary" onclick="openReassignModal('${job.id}', '${escapeHtml(job.name)}')" title="Reassign to another worker">Reassign</button>
            `;
        } else if (job.status === 'PENDING' || job.status === 'FAILED') {
            actionButtons = `
                <button class="btn btn-sm btn-danger" onclick="cancelJob('${job.id}')">Cancel</button>
            `;
        } else if (job.status === 'COMPLETED') {
            actionButtons = `
                <button class="btn btn-sm btn-secondary" onclick="cancelJob('${job.id}')" title="Cancel completed workload">Cancel</button>
            `;
        }

        tr.innerHTML = `
            <td style="font-family: var(--font-mono); font-size: 0.78rem;">${escapeHtml(job.id)}</td>
            <td>
                <div style="font-weight: 600;">${escapeHtml(job.name)}</div>
                ${job.errorMessage ? `<div style="font-size:0.72rem; color:var(--accent-rose);">${escapeHtml(job.errorMessage)}</div>` : ''}
            </td>
            <td><span class="status-pill pill-${job.status.toLowerCase()}">${job.status}</span></td>
            <td style="font-family: var(--font-mono);">${job.priority}</td>
            <td style="font-family: var(--font-mono); font-size: 0.8rem;">
                ${job.resourceRequirements ? `${job.resourceRequirements.cpuCores}c / ${job.resourceRequirements.memoryMb}M` : '-'}
            </td>
            <td>
                ${job.assignedWorkerId ? `<span class="job-chip">${escapeHtml(job.assignedWorkerId)}</span>` : '<span style="color:var(--text-muted); font-size:0.75rem;">Unassigned</span>'}
            </td>
            <td style="font-family: var(--font-mono);">${job.retryCount} / ${job.maxRetries}</td>
            <td>${depsStr}</td>
            <td style="white-space: nowrap;">${actionButtons}</td>
        `;
        el.jobsTableBody.appendChild(tr);
    });
}

function renderEvents() {
    el.eventsContainer.innerHTML = '';
    if (state.events.length === 0) {
        el.eventsContainer.innerHTML = '<div style="color:var(--text-muted); padding: 1rem;">No events recorded yet.</div>';
        return;
    }

    state.events.slice(0, 50).forEach(ev => {
        const item = document.createElement('div');
        item.className = 'event-entry';
        const d = new Date(ev.timestamp);
        const timeStr = d.toTimeString().split(' ')[0];

        item.innerHTML = `
            <span class="event-time">${timeStr}</span>
            <span class="event-type event-${ev.eventType}">${ev.eventType}</span>
            <span class="event-msg">${escapeHtml(ev.message)}</span>
        `;
        el.eventsContainer.appendChild(item);
    });
}

// Render Interactive Dependency DAG Canvas
function renderDag() {
    const canvas = el.dagCanvas;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    const width = canvas.width;
    const height = canvas.height;

    ctx.clearRect(0, 0, width, height);

    const nodes = state.graph.nodes || [];
    const edges = state.graph.edges || [];

    if (nodes.length === 0) {
        ctx.fillStyle = '#64748b';
        ctx.font = '12px JetBrains Mono';
        ctx.textAlign = 'center';
        ctx.fillText('No workload graph nodes to display.', width / 2, height / 2);
        return;
    }

    // Topological layer computation for positioning
    const indegree = {};
    const adj = {};
    nodes.forEach(n => {
        indegree[n.id] = 0;
        adj[n.id] = [];
    });

    edges.forEach(e => {
        if (adj[e.from]) adj[e.from].push(e.to);
        if (indegree[e.to] !== undefined) indegree[e.to]++;
    });

    // Compute levels
    const levels = {};
    const queue = [];
    nodes.forEach(n => {
        if (indegree[n.id] === 0) {
            levels[n.id] = 0;
            queue.push(n.id);
        }
    });

    while (queue.length > 0) {
        const curr = queue.shift();
        const currLevel = levels[curr] || 0;
        adj[curr].forEach(nxt => {
            levels[nxt] = Math.max(levels[nxt] || 0, currLevel + 1);
            queue.push(nxt);
        });
    }

    // Group nodes by layer
    const layerMap = {};
    let maxLevel = 0;
    nodes.forEach(n => {
        const lvl = levels[n.id] || 0;
        maxLevel = Math.max(maxLevel, lvl);
        if (!layerMap[lvl]) layerMap[lvl] = [];
        layerMap[lvl].push(n);
    });

    // Node coordinates map
    const nodeCoords = {};
    const layerCount = maxLevel + 1;
    const xStep = width / (layerCount + 1);

    for (let lvl = 0; lvl <= maxLevel; lvl++) {
        const layerNodes = layerMap[lvl] || [];
        const yStep = height / (layerNodes.length + 1);
        layerNodes.forEach((node, i) => {
            nodeCoords[node.id] = {
                x: xStep * (lvl + 1),
                y: yStep * (i + 1),
                node: node
            };
        });
    }

    // Draw Edges (Directional arrows)
    edges.forEach(e => {
        const src = nodeCoords[e.from];
        const dst = nodeCoords[e.to];
        if (!src || !dst) return;

        ctx.strokeStyle = 'rgba(100, 116, 139, 0.45)';
        ctx.lineWidth = 1.5;
        ctx.beginPath();
        ctx.moveTo(src.x, src.y);
        ctx.lineTo(dst.x, dst.y);
        ctx.stroke();

        // Arrow head
        const angle = Math.atan2(dst.y - src.y, dst.x - src.x);
        const arrowDist = 28;
        const arrowX = dst.x - arrowDist * Math.cos(angle);
        const arrowY = dst.y - arrowDist * Math.sin(angle);

        ctx.fillStyle = 'rgba(100, 116, 139, 0.6)';
        ctx.beginPath();
        ctx.moveTo(arrowX, arrowY);
        ctx.lineTo(arrowX - 7 * Math.cos(angle - Math.PI / 6), arrowY - 7 * Math.sin(angle - Math.PI / 6));
        ctx.lineTo(arrowX - 7 * Math.cos(angle + Math.PI / 6), arrowY - 7 * Math.sin(angle + Math.PI / 6));
        ctx.closePath();
        ctx.fill();
    });

    // Draw Nodes
    nodes.forEach(n => {
        const pos = nodeCoords[n.id];
        if (!pos) return;

        // Choose color based on status
        let strokeCol = '#64748b';
        let fillCol = '#1e293b';
        if (n.status === 'COMPLETED') strokeCol = '#10b981';
        else if (n.status === 'RUNNING') strokeCol = '#06b6d4';
        else if (n.status === 'FAILED') strokeCol = '#f43f5e';
        else if (n.ready) strokeCol = '#f59e0b';

        ctx.fillStyle = fillCol;
        ctx.strokeStyle = strokeCol;
        ctx.lineWidth = 2;

        const boxWidth = 90;
        const boxHeight = 36;
        const rx = pos.x - boxWidth / 2;
        const ry = pos.y - boxHeight / 2;

        ctx.beginPath();
        ctx.roundRect(rx, ry, boxWidth, boxHeight, 6);
        ctx.fill();
        ctx.stroke();

        ctx.fillStyle = '#f8fafc';
        ctx.font = '600 10px Plus Jakarta Sans';
        ctx.textAlign = 'center';
        const label = n.name.length > 12 ? n.name.substring(0, 11) + '..' : n.name;
        ctx.fillText(label, pos.x, pos.y - 2);

        ctx.fillStyle = strokeCol;
        ctx.font = '700 8px JetBrains Mono';
        ctx.fillText(`${n.status}`, pos.x, pos.y + 10);
    });
}

function populateDependenciesSelect() {
    el.inputJobDeps.innerHTML = '';
    state.jobs.forEach(j => {
        const opt = document.createElement('option');
        opt.value = j.id;
        opt.textContent = `${j.name} (${j.id} - ${j.status})`;
        el.inputJobDeps.appendChild(opt);
    });
}

// User Actions
el.btnResetScenario.addEventListener('click', async () => {
    setButtonLoading(el.btnResetScenario, true, 'Resetting...');
    try {
        const res = await fetch(`${API_BASE}/scenario/reset`, { method: 'POST' });
        if (res.ok) {
            showToast('Deterministic demonstration scenario reset successfully!', 'success');
            await fetchAllData();
        } else {
            showToast('Failed to reset scenario', 'error');
        }
    } catch (err) {
        showToast('Error resetting scenario: ' + err.message, 'error');
    } finally {
        setButtonLoading(el.btnResetScenario, false);
    }
});

el.btnRunCycle.addEventListener('click', async () => {
    setButtonLoading(el.btnRunCycle, true, 'Scheduling...');
    try {
        const res = await fetch(`${API_BASE}/schedule/cycle`, { method: 'POST' });
        const data = await res.json();
        if (res.ok) {
            showToast(`Cycle executed: Dispatched ${data.dispatchedCount} jobs (${data.dispatchedJobIds.join(', ') || 'None'})`, 'success');
            await fetchAllData();
        } else {
            showToast('Scheduling cycle error: ' + (data.error || 'Unknown'), 'error');
        }
    } catch (err) {
        showToast('Cycle request failed: ' + err.message, 'error');
    } finally {
        setButtonLoading(el.btnRunCycle, false);
    }
});

window.completeJob = async function(id) {
    try {
        const res = await fetch(`${API_BASE}/jobs/${id}/complete`, { method: 'POST' });
        const data = await res.json();
        if (res.ok) {
            showToast(`Job ${id} marked completed!`, 'success');
            await fetchAllData();
        } else {
            showToast('Failed: ' + (data.error || 'Error completing job'), 'error');
        }
    } catch (err) {
        showToast('Error: ' + err.message, 'error');
    }
};

window.failJob = async function(id) {
    try {
        const res = await fetch(`${API_BASE}/jobs/${id}/fail`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ error: 'Manual failure simulation' })
        });
        const data = await res.json();
        if (res.ok) {
            showToast(`Job ${id} execution failed (retry count updated)`, 'error');
            await fetchAllData();
        } else {
            showToast('Failed: ' + (data.error || 'Error failing job'), 'error');
        }
    } catch (err) {
        showToast('Error: ' + err.message, 'error');
    }
};

window.cancelJob = async function(id) {
    try {
        const res = await fetch(`${API_BASE}/jobs/${id}/cancel`, { method: 'POST' });
        const data = await res.json();
        if (res.ok) {
            showToast(`Job ${id} status set to CANCELLED`, 'info');
            await fetchAllData();
        } else {
            showToast('Cannot cancel job: ' + (data.error || 'Illegal state transition'), 'error');
        }
    } catch (err) {
        showToast('Error cancelling job: ' + err.message, 'error');
    }
};

window.openReassignModal = function(id, name) {
    el.reassignJobId.value = id;
    el.reassignJobDesc.textContent = `Reassigning active job: ${name} (${id})`;
    el.selectTargetWorker.innerHTML = '';
    state.workers.forEach(w => {
        const opt = document.createElement('option');
        opt.value = w.id;
        opt.textContent = `${w.name} (${w.availableCpuCores}c, ${w.availableMemoryMb}MB available)`;
        el.selectTargetWorker.appendChild(opt);
    });
    el.reassignModal.classList.remove('hidden');
};

el.btnCloseReassignModal.addEventListener('click', () => el.reassignModal.classList.add('hidden'));
el.btnCancelReassign.addEventListener('click', () => el.reassignModal.classList.add('hidden'));

el.reassignForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    const jobId = el.reassignJobId.value;
    const workerId = el.selectTargetWorker.value;
    try {
        const res = await fetch(`${API_BASE}/jobs/${jobId}/reassign`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ workerId })
        });
        const data = await res.json();
        if (res.ok) {
            showToast(`Job ${jobId} reassigned to ${workerId}!`, 'success');
            el.reassignModal.classList.add('hidden');
            await fetchAllData();
        } else {
            showToast('Reassignment failed: ' + (data.error || 'Error'), 'error');
        }
    } catch (err) {
        showToast('Reassignment error: ' + err.message, 'error');
    }
});

// Submit Job Modal
el.btnOpenSubmitModal.addEventListener('click', () => {
    populateDependenciesSelect();
    el.submitJobModal.classList.remove('hidden');
});

el.btnCloseSubmitModal.addEventListener('click', () => el.submitJobModal.classList.add('hidden'));
el.btnCancelSubmit.addEventListener('click', () => el.submitJobModal.classList.add('hidden'));

el.submitJobForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    const name = document.getElementById('input-job-name').value;
    const priority = parseInt(document.getElementById('input-job-priority').value, 10);
    const cpuCores = parseInt(document.getElementById('input-job-cpu').value, 10);
    const memoryMb = parseInt(document.getElementById('input-job-memory').value, 10);

    const selectedDeps = Array.from(el.inputJobDeps.selectedOptions).map(o => o.value);

    try {
        const res = await fetch(`${API_BASE}/jobs`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ name, priority, cpuCores, memoryMb, dependencies: selectedDeps })
        });
        const data = await res.json();
        if (res.ok) {
            showToast(`Job '${name}' submitted successfully!`, 'success');
            el.submitJobModal.classList.add('hidden');
            el.submitJobForm.reset();
            await fetchAllData();
        } else {
            showToast('Failed to submit job: ' + (data.error || 'Unknown error'), 'error');
        }
    } catch (err) {
        showToast('Submission error: ' + err.message, 'error');
    }
});

// Filter Buttons
document.querySelectorAll('.filter-btn').forEach(btn => {
    btn.addEventListener('click', () => {
        document.querySelectorAll('.filter-btn').forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
        state.filter = btn.dataset.filter;
        renderJobsTable();
    });
});

function escapeHtml(str) {
    if (!str) return '';
    return str.replace(/[&<>'"]/g, tag => ({
        '&': '&amp;',
        '<': '&lt;',
        '>': '&gt;',
        "'": '&#39;',
        '"': '&quot;'
    }[tag] || tag));
}

// Initial Sync & Periodic Polling (every 3 seconds)
fetchAllData();
setInterval(fetchAllData, 3000);
