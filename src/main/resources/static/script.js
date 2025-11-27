// API-bas-URL
const apiBase = '/api/workorders';

/**
 * Formaterar datum/tid från ISO-format till läsbart format
 * Exempel: 2025-11-27T23:54:03.123 → 2025-11-27 23:54
 */
function formatDateTime(dtString) {
    if (!dtString) return '';
    return dtString.replace('T', ' ').substring(0, 16);
}

// ========================================
// TAB-LOGIK (flikar)
// ========================================

document.querySelectorAll('.tab-button').forEach(btn => {
    btn.addEventListener('click', () => {
        const tab = btn.dataset.tab;

        // Ta bort "active" från alla flikar och innehåll
        document.querySelectorAll('.tab-button').forEach(b => b.classList.remove('active'));
        document.querySelectorAll('.tab-content').forEach(c => c.classList.remove('active'));

        // Sätt "active" på vald flik och innehåll
        btn.classList.add('active');
        document.getElementById('tab-' + tab).classList.add('active');
    });
});

// ========================================
// LADDA ARBETSORDER FRÅN API
// ========================================

async function loadOrders() {
    const tbody = document.querySelector('#orders-table tbody');
    const listMsg = document.getElementById('list-message');

    if (!tbody) return; // Om vi inte är på rätt flik

    tbody.innerHTML = '';
    listMsg.textContent = '';

    try {
        const response = await fetch(apiBase);
        const text = await response.text();

        if (!response.ok) {
            listMsg.textContent = 'Fel vid hämtning: ' + response.status + ' ' + text;
            return;
        }

        const orders = JSON.parse(text);

        // Om inga arbetsorder finns
        if (orders.length === 0) {
            const tr = document.createElement('tr');
            const td = document.createElement('td');
            td.colSpan = 11; // Uppdaterat för nya kolumner
            td.textContent = 'Inga arbetsorder i systemet ännu.';
            tr.appendChild(td);
            tbody.appendChild(tr);
            return;
        }

        // Skapa en rad för varje arbetsorder
        for (const wo of orders) {
            const tr = document.createElement('tr');

            tr.innerHTML = `
                <td>${wo.id}</td>
                <td>${wo.orderNumber}</td>
                <td>${wo.title}</td>
                <td>${wo.customer}</td>
                <td>${wo.trainNumber ?? ''}</td>
                <td>${wo.vehicle ?? ''}</td>
                <td>${wo.location ?? ''}</td>
                <td>${wo.category ?? ''}</td>
                <td><span class="status-badge status-${wo.status}">${wo.status}</span></td>
                <td>${formatDateTime(wo.createdAt)}</td>
                <td class="actions"></td>
            `;

            const actionsTd = tr.querySelector('.actions');

            // Skapa status-knappar
            const statuses = ['OPEN', 'IN_PROGRESS', 'COMPLETED', 'INVOICED', 'CANCELLED'];
            statuses.forEach(status => {
                const btn = document.createElement('button');
                btn.type = 'button';
                btn.textContent = status.replace('_', ' ');
                btn.onclick = (e) => {
                    e.stopPropagation(); // Förhindra att rad-klick triggas
                    updateStatus(wo.id, status);
                };
                actionsTd.appendChild(btn);
            });

            // När man klickar på raden (utom på knappar) → fyll formuläret för redigering
            tr.addEventListener('click', () => {
                fillFormFromOrder(wo);
            });

            tbody.appendChild(tr);
        }
    } catch (err) {
        listMsg.textContent = 'Tekniskt fel vid hämtning: ' + err;
    }
}
// ========================================
// LADDA "TÅG I VERKSTAD" FRÅN API
// Visar endast order med status OPEN eller IN_PROGRESS
// ========================================
async function loadTrainsView() {
    const tbody = document.querySelector('#trains-table tbody');
    const msg = document.getElementById('trains-message');

    if (!tbody) return;

    tbody.innerHTML = '';
    msg.textContent = '';

    try {
        const response = await fetch(apiBase);
        const text = await response.text();

        if (!response.ok) {
            msg.textContent = 'Fel vid hämtning: ' + response.status + ' ' + text;
            return;
        }

        const orders = JSON.parse(text);

        // Filtrera ut endast OPEN eller IN_PROGRESS
        const activeOrders = orders.filter(wo =>
            wo.status === 'OPEN' || wo.status === 'IN_PROGRESS'
        );

        if (activeOrders.length === 0) {
            const tr = document.createElement('tr');
            const td = document.createElement('td');
            td.colSpan = 6;
            td.textContent = 'Inga öppna eller pågående arbetsorder för tåg just nu.';
            tr.appendChild(td);
            tbody.appendChild(tr);
            return;
        }

        for (const wo of activeOrders) {
            const tr = document.createElement('tr');

            tr.innerHTML = `
                <td>${wo.trainNumber ?? ''}</td>
                <td>${wo.vehicle ?? ''}</td>
                <td>${wo.location ?? ''}</td>
                <td>${wo.orderNumber}</td>
                <td>${wo.title}</td>
                <td><span class="status-badge status-${wo.status}">${wo.status}</span></td>
            `;

            // Om du vill kunna hoppa till order-fliken och redigera den när man klickar:
            tr.addEventListener('click', () => {
                // byt till "Arbetsorder"-fliken
                document.querySelector('.tab-button[data-tab="orders"]').click();
                // ladda om listan (så vi har säker data)
                loadOrders().then(() => {
                    // välj rätt order i listan och fyll formuläret
                    fillFormFromOrder(wo);
                });
            });

            tbody.appendChild(tr);
        }
    } catch (err) {
        msg.textContent = 'Tekniskt fel vid hämtning: ' + err;
    }
}
// ========================================
// UPPDATERA STATUS PÅ ARBETSORDER
// ========================================

async function updateStatus(id, status) {
    try {
        const response = await fetch(`${apiBase}/${id}/status`, {
            method: 'PATCH',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ status })
        });

        if (!response.ok) {
            const text = await response.text();
            alert('Kunde inte uppdatera status: ' + response.status + ' ' + text);
            return;
        }

        // Ladda om listan efter uppdatering
        await loadOrders();
    } catch (err) {
        alert('Tekniskt fel vid statusuppdatering: ' + err);
    }
}

// ========================================
// FYLL FORMULÄRET MED DATA FRÅN EN ORDER
// (för redigering)
// ========================================

function fillFormFromOrder(wo) {
    // Sätt ID så vi vet att vi redigerar
    document.getElementById('currentId').value = wo.id;

    // Fyll alla fält
    document.getElementById('orderNumber').value = wo.orderNumber ?? '';
    document.getElementById('title').value = wo.title ?? '';
    document.getElementById('customer').value = wo.customer ?? '';
    document.getElementById('category').value = wo.category ?? '';
    document.getElementById('description').value = wo.description ?? '';
    document.getElementById('trainNumber').value = wo.trainNumber ?? '';
    document.getElementById('vehicle').value = wo.vehicle ?? '';
    document.getElementById('location').value = wo.location ?? '';

    // Ändra rubrik på formuläret
    document.getElementById('form-title').textContent = 'Redigera arbetsorder #' + wo.id;
}

// ========================================
// RENSA FORMULÄRET (för ny arbetsorder)
// ========================================

function clearForm() {
    document.getElementById('currentId').value = '';
    document.getElementById('orderNumber').value = '';
    document.getElementById('title').value = '';
    document.getElementById('customer').value = '';
    document.getElementById('category').value = '';
    document.getElementById('description').value = '';
    document.getElementById('trainNumber').value = '';
    document.getElementById('vehicle').value = '';
    document.getElementById('location').value = '';
    document.getElementById('form-title').textContent = 'Ny arbetsorder';
    document.getElementById('create-message').textContent = '';
}

// ========================================
// EVENT HANDLERS
// ========================================

// Knapp: Ladda om listan
const reloadBtn = document.getElementById('reload');
if (reloadBtn) {
    reloadBtn.addEventListener('click', loadOrders);
}
// Knapp: Ladda om "Tåg i verkstad"
const reloadTrainsBtn = document.getElementById('reload-trains');
if (reloadTrainsBtn) {
    reloadTrainsBtn.addEventListener('click', loadTrainsView);
}
// Knapp: Ny arbetsorder (rensar formuläret)
const clearBtn = document.getElementById('clear-form');
if (clearBtn) {
    clearBtn.addEventListener('click', clearForm);
}

// Formulär: Skapa eller uppdatera arbetsorder
const createForm = document.getElementById('create-form');
if (createForm) {
    createForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        const msg = document.getElementById('create-message');
        msg.textContent = '';
        msg.className = '';

        // Kolla om vi redigerar (currentId har värde) eller skapar ny
        const id = document.getElementById('currentId').value || null;

        // Samla data från formuläret
        const payload = {
            orderNumber: document.getElementById('orderNumber').value,
            title: document.getElementById('title').value,
            customer: document.getElementById('customer').value,
            category: document.getElementById('category').value,
            description: document.getElementById('description').value,
            trainNumber: document.getElementById('trainNumber').value,
            vehicle: document.getElementById('vehicle').value,
            location: document.getElementById('location').value
        };

        // Bestäm URL och metod beroende på om vi skapar eller uppdaterar
        const url = id ? `${apiBase}/${id}` : apiBase;
        const method = id ? 'PUT' : 'POST';

        try {
            const response = await fetch(url, {
                method,
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(payload)
            });

            const text = await response.text();

            if (!response.ok) {
                msg.textContent = 'Fel: ' + response.status + ' ' + text;
                msg.className = 'error';
                return;
            }

            // Visa framgångsmeddelande
            msg.textContent = id ? 'Arbetsorder uppdaterad.' : 'Arbetsorder skapad.';
            msg.className = 'success';

            // Rensa formuläret efter skapande (valfritt)
            if (!id) {
                clearForm();
            }

            // Ladda om listan
            await loadOrders();
        } catch (err) {
            msg.textContent = 'Tekniskt fel: ' + err;
            msg.className = 'error';
        }
    });
}

// ========================================
// INITIAL LADDNING
// ========================================

// Ladda arbetsorder när sidan laddas
loadOrders();
// Ladda "Tåg i verkstad" också
loadTrainsView();