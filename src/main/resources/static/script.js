// File: src/main/resources/static/script.js

const apiBase = '/api/workorders';

let allOrders = [];
let currentView = 'active';
let currentDetailOrder = null;
let timeLines = [];
let materialLines = [];

/* Init & flikar */
document.querySelectorAll('.tab-button').forEach(btn => {
    btn.addEventListener('click', () => {
        document.querySelectorAll('.tab-button').forEach(b => b.classList.remove('active'));
        document.querySelectorAll('.tab-content').forEach(c => c.classList.remove('active'));
        btn.classList.add('active');
        const tab = btn.getAttribute('data-tab');
        const el = document.getElementById('tab-' + tab);
        if (el) el.classList.add('active');

        // Dölj overlays vid flikbyte
        const detail = document.getElementById('order-detail-view');
        const modal = document.getElementById('create-order-modal');
        if (detail) detail.style.display = 'none';
        if (modal) modal.style.display = 'none';

        if (tab === 'trains') loadTrainsView();
        if (tab === 'orders') loadOrders();
    });
});

/* UI bindings */
const viewSelect = document.getElementById('order-view');
if (viewSelect) {
    viewSelect.addEventListener('change', (e) => {
        currentView = e.target.value;
        renderOrdersForCurrentView();
    });
}

const reloadBtn = document.getElementById('reload');
if (reloadBtn) reloadBtn.addEventListener('click', loadOrders);
const reloadTrainsBtn = document.getElementById('reload-trains');
if (reloadTrainsBtn) reloadTrainsBtn.addEventListener('click', loadTrainsView);

// Ny order-knapp (finns i alla vyer under Arbetsordrar)
const newOrderBtn = document.getElementById('new-order-button');
if (newOrderBtn) newOrderBtn.addEventListener('click', () => openCreateModal());

// Modal stäng/avbryt
const closeCreateBtn = document.getElementById('close-create-modal');
if (closeCreateBtn) closeCreateBtn.addEventListener('click', closeCreateModal);
const createCancelBtn = document.getElementById('create-modal-cancel');
if (createCancelBtn) createCancelBtn.addEventListener('click', closeCreateModal);

/* Ladda ordrar */
async function loadOrders() {
    const msg = document.getElementById('list-message');
    if (msg) msg.textContent = '';
    try {
        const res = await fetch(apiBase);
        const txt = await res.text();
        if (!res.ok) {
            if (msg) msg.textContent = `Fel vid hämtning: ${res.status} ${txt}`;
            return;
        }
        allOrders = JSON.parse(txt);
        renderOrdersForCurrentView();
    } catch (err) {
        if (msg) msg.textContent = `Tekniskt fel vid hämtning: ${err}`;
    }
}

/* Rendera ordrar beroende på vy — grupperat per location */
function renderOrdersForCurrentView() {
    // Först hitta sektionen att rendera i
    const tableEl = document.getElementById('orders-table');
    let section;
    if (tableEl) {
        section = tableEl.closest('section') || tableEl.parentElement;
    } else {
        // fallback: hitta via titel
        const titleEl = document.getElementById('orders-list-title');
        section = titleEl ? titleEl.parentElement : document.querySelector('#tab-orders');
    }
    if (!section) return;

    // Hitta eller skapa container för grupperade tabeller
    let container = section.querySelector('#orders-tables-container');
    if (!container) {
        container = document.createElement('div');
        container.id = 'orders-tables-container';
        // placera efter titeln och knapparna (före eventuell gammal tabell)
        const reference = section.querySelector('#orders-table');
        if (reference) {
            reference.style.display = 'none'; // göm gammal single-table om den finns
            reference.insertAdjacentElement('afterend', container);
        } else {
            section.appendChild(container);
        }
    }

    container.innerHTML = ''; // töm container

    // Filtrera ordrar enligt vy
    let filtered = [];
    switch (currentView) {
        case 'active':
            filtered = allOrders.filter(wo => (wo.status === 'OPEN' || wo.status === 'IN_PROGRESS') && !wo.archivedAt);
            break;
        case 'done':
            filtered = allOrders.filter(wo => wo.status === 'COMPLETED' && !wo.archivedAt);
            break;
        case 'finance':
            filtered = allOrders.filter(wo => (wo.status === 'READY_FOR_INVOICING' || wo.status === 'INVOICED') && !wo.archivedAt);
            break;
        case 'archive':
            filtered = allOrders.filter(wo => wo.archivedAt || wo.status === 'CANCELLED');
            break;
        default:
            filtered = allOrders;
    }

    // Om inga ordrar, visa meddelande
    const title = document.getElementById('orders-list-title');
    if (title) {
        switch (currentView) {
            case 'active': title.textContent = 'Aktiva arbetsorder'; break;
            case 'done': title.textContent = 'Klara arbetsorder'; break;
            case 'finance': title.textContent = 'Ekonomi'; break;
            case 'archive': title.textContent = 'Arkiv'; break;
            default: title.textContent = 'Arbetsorder';
        }
    }

    if (!filtered || filtered.length === 0) {
        container.innerHTML = '<p>Inga arbetsorder i denna vy.</p>';
        return;
    }

    // Grupp per plats (location)
    const groups = {};
    filtered.forEach(wo => {
        const loc = (wo.location || 'Okänd plats').trim() || 'Okänd plats';
        if (!groups[loc]) groups[loc] = [];
        groups[loc].push(wo);
    });

    // För varje grupp, skapa en block med titel och tabell
    Object.keys(groups).sort().forEach(loc => {
        const ords = groups[loc];

        const block = document.createElement('div');
        block.className = 'train-location-block';
        block.style.marginTop = '10px';

        const titleDiv = document.createElement('div');
        titleDiv.className = 'train-location-title';
        titleDiv.textContent = loc;
        block.appendChild(titleDiv);

        const table = document.createElement('table');
        table.className = 'orders-group-table';
        table.innerHTML = `
            <thead>
                <tr>
                    <th>ID</th>
                    <th>Ordernr</th>
                    <th>Titel</th>
                    <th>Kund</th>
                    <th>Tåg</th>
                    <th>Fordon</th>
                    <th>Plats</th>
                    <th>Spår</th>
                    <th>Kategori</th>
                    <th>Status</th>
                    <th>Skapad</th>
                    <th>Åtgärder</th>
                </tr>
            </thead>
            <tbody></tbody>
        `;
        const tbody = table.querySelector('tbody');

        ords.forEach(wo => {
            const tr = document.createElement('tr');
            tr.innerHTML = `
                <td>${wo.id}</td>
                <td>${escapeHtml(wo.orderNumber)}</td>
                <td>${escapeHtml(wo.title)}</td>
                <td>${escapeHtml(wo.customer)}</td>
                <td>${escapeHtml(wo.trainNumber ?? '')}</td>
                <td>${escapeHtml(wo.vehicle ?? '')}</td>
                <td>${escapeHtml(wo.location ?? '')}</td>
                <td>${escapeHtml(wo.track ?? '')}</td>
                <td>${escapeHtml(wo.category ?? '')}</td>
                <td><span class="status-badge status-${wo.status}">${translateStatus(wo.status)}</span></td>
                <td>${formatDateTime(wo.createdAt)}</td>
                <td>
                    <button onclick="openOrderDetail(${wo.id})">Öppna</button>
                    <button onclick="openEditModal(${wo.id})">Redigera</button>
                    <div style="margin-top:6px;">
                        <select onchange="changeStatus(${wo.id}, this.value)">
                            <option value="">Ändra status...</option>
                            <option value="OPEN">Öppen</option>
                            <option value="IN_PROGRESS">Pågående</option>
                            <option value="COMPLETED">Klar</option>
                            <option value="READY_FOR_INVOICING">Klar för fakturering</option>
                            <option value="INVOICED">Fakturerad</option>
                            <option value="CANCELLED">Avbruten</option>
                        </select>
                    </div>
                </td>
            `;
            tbody.appendChild(tr);
        });

        block.appendChild(table);
        container.appendChild(block);
    });
}

/* Hjälpfunktioner */
function translateStatus(status) {
    const map = {
        'OPEN': 'Öppen',
        'IN_PROGRESS': 'Pågående',
        'COMPLETED': 'Klar',
        'READY_FOR_INVOICING': 'Klar för fakturering',
        'INVOICED': 'Fakturerad',
        'CANCELLED': 'Avbruten'
    };
    return map[status] || status;
}

function formatDateTime(dt) {
    if (!dt) return '';
    try {
        const d = new Date(dt);
        return `${d.toLocaleDateString('sv-SE')} ${d.toLocaleTimeString('sv-SE',{hour:'2-digit', minute:'2-digit'})}`;
    } catch (e) {
        return dt;
    }
}

function escapeHtml(s) {
    if (s === null || s === undefined) return '';
    return String(s)
        .replaceAll('&','&amp;')
        .replaceAll('<','&lt;')
        .replaceAll('>','&gt;')
        .replaceAll('"','&quot;')
        .replaceAll("'", '&#39;');
}

/* Ändra status */
async function changeStatus(id, newStatus) {
    if (!newStatus) return;
    try {
        const res = await fetch(`${apiBase}/${id}/status?status=${encodeURIComponent(newStatus)}`, { method: 'PATCH' });
        const txt = await res.text();
        if (!res.ok) {
            alert(`Fel vid statusändring: ${res.status} ${txt}`);
            return;
        }
        await loadOrders();
        await loadTrainsView();
    } catch (err) {
        alert('Tekniskt fel vid statusändring: ' + err);
    }
}

/* Öppna detaljvy (overlay) */
async function openOrderDetail(id) {
    try {
        const res = await fetch(`${apiBase}/${id}`);
        const txt = await res.text();
        if (!res.ok) {
            alert('Kunde inte hämta arbetsorder: ' + res.status + ' ' + txt);
            return;
        }
        const wo = JSON.parse(txt);
        currentDetailOrder = wo;

        document.getElementById('detail-orderNumber').textContent = wo.orderNumber || '';
        document.getElementById('detail-customer').textContent = wo.customer || '';
        document.getElementById('detail-createdAt').textContent = formatDateTime(wo.createdAt);
        document.getElementById('detail-trainNumber').textContent = wo.trainNumber || '';
        document.getElementById('detail-vehicle').textContent = wo.vehicle || '';
        document.getElementById('detail-location').textContent = wo.location || '';
        document.getElementById('detail-track').textContent = wo.track || '';
        document.getElementById('detail-title').textContent = wo.title || '';
        document.getElementById('detail-description').textContent = wo.description || '';

        await loadTimeEntries(id);
        await loadMaterialEntries(id);

        document.getElementById('order-detail-view').style.display = 'block';
    } catch (err) {
        alert('Tekniskt fel vid öppning: ' + err);
    }
}
const closeDetailBtn = document.getElementById('close-detail');
if (closeDetailBtn) closeDetailBtn.addEventListener('click', () => {
    const dv = document.getElementById('order-detail-view');
    if (dv) dv.style.display = 'none';
});

/* Tid & material - hämta/spara (fixad input-hantering så fokus bibehålls) */
async function loadTimeEntries(workOrderId) {
    try {
        const res = await fetch(`${apiBase}/${workOrderId}/time-entries`);
        const txt = await res.text();
        timeLines = res.ok ? JSON.parse(txt) : [];
        renderTimeLines(); // renderar en gång när vi hämtat
        updateTotals();
    } catch (err) {
        console.error('Tekniskt fel tid:', err);
        timeLines = [];
        renderTimeLines();
        updateTotals();
    }
}

async function loadMaterialEntries(workOrderId) {
    try {
        const res = await fetch(`${apiBase}/${workOrderId}/material-entries`);
        const txt = await res.text();
        materialLines = res.ok ? JSON.parse(txt) : [];
        renderMaterialLines(); // renderar en gång när vi hämtat
        updateTotals();
    } catch (err) {
        console.error('Tekniskt fel material:', err);
        materialLines = [];
        renderMaterialLines();
        updateTotals();
    }
}

function renderTimeLines() {
    const tbody = document.querySelector('#time-table tbody');
    if (!tbody) return;
    tbody.innerHTML = '';

    timeLines.forEach((line, idx) => {
        const tr = document.createElement('tr');
        tr.dataset.index = idx;
        tr.innerHTML = `
            <td><input data-field="action" value="${escapeHtml(line.action || '')}"></td>
            <td><input data-field="work" value="${escapeHtml(line.work || '')}"></td>
            <td><input data-field="hours" type="number" step="0.1" value="${line.hours || 0}"></td>
            <td><input data-field="rate" type="number" step="1" value="${line.rate || 0}"></td>
            <td class="sum-cell">${(line.total || 0).toFixed(2)} kr</td>
            <td><button class="remove-time-btn">Ta bort</button></td>
        `;
        tbody.appendChild(tr);
    });

    // Delegated input handler: uppdatera modell och summa utan att re-rendera raderna (fokus bibehålls)
    tbody.oninput = function(e) {
        const input = e.target;
        const tr = input.closest('tr');
        if (!tr) return;
        const idx = Number(tr.dataset.index);
        const field = input.dataset.field;
        if (isNaN(idx) || idx < 0 || idx >= timeLines.length) return;

        const val = input.value;
        if (field === 'hours' || field === 'rate') {
            timeLines[idx][field] = parseFloat(val) || 0;
            timeLines[idx].total = (timeLines[idx].hours || 0) * (timeLines[idx].rate || 0);
            // uppdatera bara sum-cell för den raden
            const sumCell = tr.querySelector('.sum-cell');
            if (sumCell) sumCell.textContent = (timeLines[idx].total || 0).toFixed(2) + ' kr';
            updateTotals();
        } else {
            timeLines[idx][field] = val;
        }
    };

    // Delegated click for remove buttons
    tbody.onclick = function(e) {
        const btn = e.target.closest('.remove-time-btn');
        if (!btn) return;
        const tr = btn.closest('tr');
        const idx = Number(tr.dataset.index);
        if (isNaN(idx)) return;
        timeLines.splice(idx, 1);
        renderTimeLines(); // rendera om efter borttagning (okej att re-rendera här)
        updateTotals();
    };
}

function renderMaterialLines() {
    const tbody = document.querySelector('#material-table tbody');
    if (!tbody) return;
    tbody.innerHTML = '';

    materialLines.forEach((line, idx) => {
        const tr = document.createElement('tr');
        tr.dataset.index = idx;
        tr.innerHTML = `
            <td><input data-field="articleNumber" value="${escapeHtml(line.articleNumber || '')}" data-row="${idx}"></td>
            <td><input data-field="description" value="${escapeHtml(line.description || '')}"></td>
            <td><input data-field="quantity" type="number" step="1" value="${line.quantity || 0}"></td>
            <td><input data-field="unit" value="${escapeHtml(line.unit || 'st')}"></td>
            <td><input data-field="price" type="number" step="0.01" value="${line.price || 0}"></td>
            <td class="sum-cell">${(line.total || 0).toFixed(2)} kr</td>
            <td><button class="remove-material-btn">Ta bort</button></td>
        `;
        tbody.appendChild(tr);
    });

    // Delegated input handler för material (inkl. autocomplete)
    tbody.oninput = function(e) {
        const input = e.target;
        const tr = input.closest('tr');
        if (!tr) return;
        const idx = Number(tr.dataset.index);
        const field = input.dataset.field;
        if (isNaN(idx) || idx < 0 || idx >= materialLines.length) return;

        const val = input.value;

        if (field === 'quantity' || field === 'price') {
            materialLines[idx][field] = parseFloat(val) || 0;
            materialLines[idx].total = (materialLines[idx].quantity || 0) * (materialLines[idx].price || 0);
            const sumCell = tr.querySelector('.sum-cell');
            if (sumCell) sumCell.textContent = (materialLines[idx].total || 0).toFixed(2) + ' kr';
            updateTotals();
        } else {
            materialLines[idx][field] = val;

            // Aktivera autocomplete på articleNumber
            if (field === 'articleNumber') {
                debouncedSuggest(input, val, idx);
            }
        }
    };

    // Delegated click for remove buttons
    tbody.onclick = function(e) {
        const btn = e.target.closest('.remove-material-btn');
        if (!btn) return;
        const tr = btn.closest('tr');
        const idx = Number(tr.dataset.index);
        if (isNaN(idx)) return;
        materialLines.splice(idx, 1);
        renderMaterialLines(); // re-render efter borttagning
        updateTotals();
    };
}

document.getElementById('add-time-row')?.addEventListener('click', () => {
    timeLines.push({ action: '', work: '', hours: 0, rate: 850, total: 0 });
    renderTimeLines();
    updateTotals();
});
document.getElementById('add-material-row')?.addEventListener('click', () => {
    materialLines.push({ articleNumber: '', description: '', quantity: 0, unit: 'st', price: 0, total: 0 });
    renderMaterialLines();
    updateTotals();
});

function updateTotals() {
    const timeTotal = timeLines.reduce((s, l) => s + (l.total || 0), 0);
    const materialTotal = materialLines.reduce((s, l) => s + (l.total || 0), 0);
    const timeEl = document.getElementById('time-total');
    if (timeEl) timeEl.textContent = timeTotal.toFixed(2) + ' kr';
    const materialEl = document.getElementById('material-total');
    if (materialEl) materialEl.textContent = materialTotal.toFixed(2) + ' kr';
    const grandEl = document.getElementById('grand-total');
    if (grandEl) grandEl.textContent = (timeTotal + materialTotal).toFixed(2) + ' kr';
}

/* Spara tid/material */
document.getElementById('save-detail')?.addEventListener('click', async () => {
    const msg = document.getElementById('detail-message');
    if (msg) msg.textContent = 'Sparar...';
    if (!currentDetailOrder) {
        if (msg) {
            msg.textContent = 'Ingen order vald';
            msg.classList.add('error');
        }
        return;
    }
    const id = currentDetailOrder.id;
    try {
        const tRes = await fetch(`${apiBase}/${id}/time-entries`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(timeLines)
        });
        if (!tRes.ok) {
            const txt = await tRes.text();
            if (msg) {
                msg.textContent = `Fel vid sparande tid: ${tRes.status} ${txt}`;
                msg.classList.add('error');
            }
            return;
        }
        const mRes = await fetch(`${apiBase}/${id}/material-entries`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(materialLines)
        });
        if (!mRes.ok) {
            const txt = await mRes.text();
            if (msg) {
                msg.textContent = `Fel vid sparande material: ${mRes.status} ${txt}`;
                msg.classList.add('error');
            }
            return;
        }
        if (msg) {
            msg.textContent = 'Ändringar sparade!';
            msg.classList.remove('error');
        }
        await loadTimeEntries(id);
        await loadMaterialEntries(id);
        await loadOrders();
    } catch (err) {
        if (msg) {
            msg.textContent = 'Tekniskt fel vid sparande: ' + err;
            msg.classList.add('error');
        }
    }
});

/* Tåg i verkstad */
async function loadTrainsView() {
    const container = document.getElementById('trains-container');
    const msg = document.getElementById('trains-message');
    if (container) container.innerHTML = '';
    if (msg) msg.textContent = '';
    try {
        const res = await fetch(apiBase);
        const txt = await res.text();
        if (!res.ok) {
            if (msg) msg.textContent = `Fel vid hämtning: ${res.status} ${txt}`;
            return;
        }
        const orders = JSON.parse(txt);
        const active = orders.filter(wo => (wo.status === 'OPEN' || wo.status === 'IN_PROGRESS') && !wo.archivedAt);
        if (active.length === 0) {
            if (container) container.innerHTML = '<p>Inga tåg i verkstad just nu.</p>';
            return;
        }
        const grouped = {};
        active.forEach(wo => {
            const loc = wo.location || 'Okänd plats';
            if (!grouped[loc]) grouped[loc] = [];
            grouped[loc].push(wo);
        });
        for (const [loc, ords] of Object.entries(grouped)) {
            const block = document.createElement('div');
            block.className = 'train-location-block';
            const title = document.createElement('div');
            title.className = 'train-location-title';
            title.textContent = loc;
            block.appendChild(title);
            const table = document.createElement('table');
            table.innerHTML = `<thead><tr><th>Tåg</th><th>Fordon</th><th>Plats</th><th>Spår</th><th>Ordernr</th><th>Titel</th><th>Status</th><th>Åtgärd</th></tr></thead><tbody></tbody>`;
            const tbody = table.querySelector('tbody');
            ords.forEach(wo => {
                const tr = document.createElement('tr');
                tr.innerHTML = `
                    <td>${escapeHtml(wo.trainNumber ?? '')}</td>
                    <td>${escapeHtml(wo.vehicle ?? '')}</td>
                    <td>${escapeHtml(wo.location ?? '')}</td>
                    <td>${escapeHtml(wo.track ?? '')}</td>
                    <td>${escapeHtml(wo.orderNumber)}</td>
                    <td>${escapeHtml(wo.title)}</td>
                    <td><span class="status-badge status-${wo.status}">${translateStatus(wo.status)}</span></td>
                    <td><button onclick="openOrderDetail(${wo.id})">Öppna</button></td>
                `;
                tbody.appendChild(tr);
            });
            block.appendChild(table);
            container.appendChild(block);
        }
    } catch (err) {
        if (msg) msg.textContent = 'Tekniskt fel vid hämtning: ' + err;
    }
}

/* Modal: skapa / redigera */
function openCreateModal() {
    const idEl = document.getElementById('createModal-id');
    if (idEl) idEl.value = '';
    document.getElementById('create-modal-title') && (document.getElementById('create-modal-title').textContent = 'Ny arbetsorder');
    document.getElementById('createModal-orderNumber') && (document.getElementById('createModal-orderNumber').value = '');
    document.getElementById('createModal-title') && (document.getElementById('createModal-title').value = '');
    document.getElementById('createModal-customer') && (document.getElementById('createModal-customer').value = '');
    document.getElementById('createModal-trainNumber') && (document.getElementById('createModal-trainNumber').value = '');
    document.getElementById('createModal-vehicle') && (document.getElementById('createModal-vehicle').value = '');
    document.getElementById('createModal-location') && (document.getElementById('createModal-location').value = '');
    document.getElementById('createModal-track') && (document.getElementById('createModal-track').value = '');
    document.getElementById('createModal-category') && (document.getElementById('createModal-category').value = '');
    document.getElementById('createModal-description') && (document.getElementById('createModal-description').value = '');
    document.getElementById('create-modal-message') && (document.getElementById('create-modal-message').textContent = '');
    document.getElementById('create-order-modal').style.display = 'block';
}

function closeCreateModal() {
    const modal = document.getElementById('create-order-modal');
    if (modal) modal.style.display = 'none';
}

async function openEditModal(id) {
    try {
        const res = await fetch(`${apiBase}/${id}`);
        const txt = await res.text();
        if (!res.ok) {
            alert('Kunde inte hämta arbetsorder för redigering: ' + res.status + ' ' + txt);
            return;
        }
        const wo = JSON.parse(txt);
        document.getElementById('create-modal-title') && (document.getElementById('create-modal-title').textContent = 'Redigera arbetsorder');
        document.getElementById('createModal-id') && (document.getElementById('createModal-id').value = wo.id || '');
        document.getElementById('createModal-orderNumber') && (document.getElementById('createModal-orderNumber').value = wo.orderNumber || '');
        document.getElementById('createModal-title') && (document.getElementById('createModal-title').value = wo.title || '');
        document.getElementById('createModal-customer') && (document.getElementById('createModal-customer').value = wo.customer || '');
        document.getElementById('createModal-trainNumber') && (document.getElementById('createModal-trainNumber').value = wo.trainNumber || '');
        document.getElementById('createModal-vehicle') && (document.getElementById('createModal-vehicle').value = wo.vehicle || '');
        document.getElementById('createModal-location') && (document.getElementById('createModal-location').value = wo.location || '');
        document.getElementById('createModal-track') && (document.getElementById('createModal-track').value = wo.track || '');
        document.getElementById('createModal-category') && (document.getElementById('createModal-category').value = wo.category || '');
        document.getElementById('createModal-description') && (document.getElementById('createModal-description').value = wo.description || '');
        document.getElementById('create-modal-message') && (document.getElementById('create-modal-message').textContent = '');
        document.getElementById('create-order-modal').style.display = 'block';
    } catch (err) {
        alert('Tekniskt fel: ' + err);
    }
}

/* Submit modal: POST (ny) eller PUT (uppdatera) */
document.getElementById('create-modal-form')?.addEventListener('submit', async (e) => {
    e.preventDefault();
    const msgEl = document.getElementById('create-modal-message');
    if (msgEl) msgEl.textContent = 'Sparar...';

    const id = document.getElementById('createModal-id')?.value;
    const payload = {
        orderNumber: document.getElementById('createModal-orderNumber')?.value,
        title: document.getElementById('createModal-title')?.value,
        customer: document.getElementById('createModal-customer')?.value,
        trainNumber: document.getElementById('createModal-trainNumber')?.value || null,
        vehicle: document.getElementById('createModal-vehicle')?.value || null,
        location: document.getElementById('createModal-location')?.value || null,
        track: document.getElementById('createModal-track')?.value || null,
        category: document.getElementById('createModal-category')?.value || null,
        description: document.getElementById('createModal-description')?.value || null
    };

    try {
        let res;
        if (id) {
            res = await fetch(`${apiBase}/${id}`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });
        } else {
            res = await fetch(apiBase, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });
        }
        const text = await res.text();
        if (!res.ok) {
            if (msgEl) {
                msgEl.textContent = `Fel: ${res.status} ${text}`;
                msgEl.classList.add('error');
            }
            return;
        }
        if (msgEl) {
            msgEl.textContent = id ? 'Arbetsorder uppdaterad!' : 'Arbetsorder skapad!';
            msgEl.classList.remove('error');
        }
        closeCreateModal();
        await loadOrders();
    } catch (err) {
        if (msgEl) {
            msgEl.textContent = 'Tekniskt fel: ' + err;
            msgEl.classList.add('error');
        }
    }
});

/* --- START: Price lookup / autocomplete helpers --- */

// Debounce helper
function debounce(fn, wait) {
    let t;
    return function (...args) {
        clearTimeout(t);
        t = setTimeout(() => fn.apply(this, args), wait);
    };
}

// Fetch exact price by emNr
async function fetchPriceByEm(emNr) {
    if (!emNr || !emNr.trim()) return null;
    try {
        const res = await fetch(`/api/pricelist/${encodeURIComponent(emNr.trim())}`);
        if (!res.ok) return null;
        return await res.json();
    } catch (e) {
        console.error('Price lookup error', e);
        return null;
    }
}

// Fetch suggestions by prefix
async function fetchPriceSuggestions(prefix) {
    if (!prefix || !prefix.trim()) return [];
    try {
        const res = await fetch(`/api/pricelist/search?prefix=${encodeURIComponent(prefix.trim())}`);
        if (!res.ok) return [];
        return await res.json();
    } catch (e) {
        console.error('Price suggestions error', e);
        return [];
    }
}

// Ritar förslag-lista under ett input-element
function showSuggestionsForInput(inputEl, suggestions, onSelect) {
    removeSuggestionBox(inputEl);

    if (!suggestions || suggestions.length === 0) return;

    const box = document.createElement('div');
    box.className = 'autocomplete-box';
    box.style.position = 'absolute';
    box.style.zIndex = 9999;
    box.style.background = '#fff';
    box.style.border = '1px solid #ccc';
    box.style.maxHeight = '200px';
    box.style.overflow = 'auto';
    box.style.minWidth = (inputEl.offsetWidth) + 'px';
    box.style.boxShadow = '0 2px 6px rgba(0,0,0,0.15)';
    suggestions.forEach(item => {
        const row = document.createElement('div');
        row.className = 'autocomplete-row';
        row.style.padding = '6px';
        row.style.cursor = 'pointer';
        row.textContent = `${item.emNr} — ${item.name} — ${item.unit} — ${item.price}`;
        row.addEventListener('mousedown', (ev) => {
            ev.preventDefault();
            onSelect(item);
            removeSuggestionBox(inputEl);
        });
        box.appendChild(row);
    });

    const rect = inputEl.getBoundingClientRect();
    box.style.left = (rect.left + window.scrollX) + 'px';
    box.style.top = (rect.bottom + window.scrollY) + 'px';

    box.dataset.for = inputEl.name || '';
    document.body.appendChild(box);

    inputEl.addEventListener('blur', function _close() {
        setTimeout(() => removeSuggestionBox(inputEl), 150);
        inputEl.removeEventListener('blur', _close);
    });
}

function removeSuggestionBox(inputEl) {
    const boxes = Array.from(document.querySelectorAll('.autocomplete-box'));
    boxes.forEach(b => b.remove());
}

// När användaren skriver i articleNumber-fält -> sök förslag (debounced)
const debouncedSuggest = debounce(async function(inputEl, prefix, rowIdx) {
    const suggestions = await fetchPriceSuggestions(prefix);
    showSuggestionsForInput(inputEl, suggestions, (item) => {
        fillMaterialRowFromSuggestion(rowIdx, item);
    });
}, 250);

// Fyll en material-rad (index) med data från PriceItem
function fillMaterialRowFromSuggestion(rowIdx, item) {
    if (!item) return;
    const tbody = document.querySelector('#material-table tbody');
    if (!tbody) return;
    const tr = tbody.querySelector(`tr[data-index="${rowIdx}"]`);
    if (!tr) return;

    materialLines[rowIdx].articleNumber = item.emNr;
    materialLines[rowIdx].description = item.name;
    materialLines[rowIdx].unit = item.unit;
    materialLines[rowIdx].price = item.price;
    materialLines[rowIdx].total = (materialLines[rowIdx].quantity || 0) * materialLines[rowIdx].price;

    const artInput = tr.querySelector('input[data-field="articleNumber"]');
    const descInput = tr.querySelector('input[data-field="description"]');
    const unitInput = tr.querySelector('input[data-field="unit"]');
    const priceInput = tr.querySelector('input[data-field="price"]');
    const sumCell = tr.querySelector('.sum-cell');

    if (artInput) artInput.value = item.emNr;
    if (descInput) descInput.value = item.name;
    if (unitInput) unitInput.value = item.unit;
    if (priceInput) priceInput.value = item.price;
    if (sumCell) sumCell.textContent = (materialLines[rowIdx].total || 0).toFixed(2) + ' kr';

    updateTotals();
}

/* --- END: Price lookup / autocomplete helpers --- */

/* Init load */
loadOrders();