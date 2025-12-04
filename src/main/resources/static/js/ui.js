(function ($) {
    const state = {
        token: null,
        role: null,
        email: null
    };

    const $log = $('#logOutput');
    const $authBadge = $('#authStatusBadge');
    const $roleBadge = $('#roleBadge');
    const $tokenPreview = $('#tokenPreview');
    const $alertArea = $('#alertArea');

    function escapeHtml(value = '') {
        return value
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    function randomKey() {
        if (window.crypto && window.crypto.randomUUID) {
            return window.crypto.randomUUID();
        }
        return `idem-${Date.now()}-${Math.random().toString(16).slice(2)}`;
    }

    function updateAuthDisplay() {
        if (state.token) {
            $authBadge.text(state.email || '로그인됨').removeClass('bg-secondary').addClass('bg-success');
            $roleBadge.text(state.role || '-');
            $tokenPreview.val(`Bearer ${state.token}`);
        } else {
            $authBadge.text('게스트').removeClass('bg-success').addClass('bg-secondary');
            $roleBadge.text('-');
            $tokenPreview.val('');
        }
        const isAdmin = state.role === 'ADMIN';
        $('[data-admin-only]').toggleClass('opacity-50', !isAdmin)
            .find('input,button').prop('disabled', !isAdmin);
    }

    function showAlert(message, type = 'danger', detail) {
        if (!message) {
            return;
        }
        const safeMessage = escapeHtml(message);
        const safeDetail = detail ? `<div class="small mt-1">${escapeHtml(detail)}</div>` : '';
        const $alert = $(`
            <div class="alert alert-${type} alert-dismissible fade show" role="alert">
                ${safeMessage}${safeDetail}
                <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Close"></button>
            </div>
        `);
        $alertArea.append($alert);
        setTimeout(() => $alert.alert('close'), 5000);
    }

    function logResponse(label, payload) {
        const pretty = JSON.stringify({ label, payload }, null, 2);
        $log.text(pretty);
    }

    function handleError(jqXHR, context = {}) {
        const detailMessage = jqXHR.responseJSON ? jqXHR.responseJSON.message : (jqXHR.statusText || 'Unknown error');
        const summary = `${context.method || ''} ${context.url || ''}`.trim();
        const statusLine = `${summary ? summary + ' -> ' : ''}${jqXHR.status} ${jqXHR.statusText}`;
        showAlert(statusLine || '요청 실패', 'danger', detailMessage);
        logResponse('error', {
            status: jqXHR.status,
            message: detailMessage,
            response: jqXHR.responseJSON || jqXHR.responseText
        });
    }

    function ajaxJson(method, url, body, extraHeaders = {}) {
        const headers = Object.assign({}, extraHeaders);
        if (state.token) {
            headers['Authorization'] = `Bearer ${state.token}`;
        }
        return $.ajax({
            method,
            url,
            contentType: 'application/json',
            dataType: 'json',
            data: body ? JSON.stringify(body) : undefined,
            headers
        });
    }

    function ensureAuthOrLog() {
        if (!state.token) {
            logResponse('error', { message: '로그인이 필요합니다. 먼저 JWT를 발급받으세요.' });
            showAlert('로그인이 필요합니다. 먼저 JWT를 발급받으세요.', 'warning');
            return false;
        }
        return true;
    }

    $('#registerForm').on('submit', function (e) {
        e.preventDefault();
        const form = e.target;
        const payload = {
            name: form.name.value.trim(),
            email: form.email.value.trim(),
            password: form.password.value
        };
        ajaxJson('POST', '/api/v1/auth/register', payload)
            .done(res => {
                state.token = res.accessToken;
                state.role = res.role;
                state.email = res.email;
                updateAuthDisplay();
                logResponse('register', res);
            })
            .fail(jqXHR => handleError(jqXHR, { method: 'POST', url: '/api/v1/auth/register' }));
    });

    $('#loginForm').on('submit', function (e) {
        e.preventDefault();
        const form = e.target;
        const payload = {
            email: form.email.value.trim(),
            password: form.password.value
        };
        ajaxJson('POST', '/api/v1/auth/login', payload)
            .done(res => {
                state.token = res.accessToken;
                state.role = res.role;
                state.email = res.email;
                updateAuthDisplay();
                logResponse('login', res);
            })
            .fail(jqXHR => handleError(jqXHR, { method: 'POST', url: '/api/v1/auth/login' }));
    });

    $('#loadProductsBtn').on('click', function () {
        const listUrl = '/api/v1/products?page=0&size=50';
        ajaxJson('GET', listUrl)
            .done(res => {
                renderProducts(res.items || []);
                logResponse('products', res);
            })
            .fail(jqXHR => handleError(jqXHR, { method: 'GET', url: listUrl }));
    });

    function renderProducts(items) {
        const $tbody = $('#productsTable tbody');
        if (!items.length) {
            $tbody.html('<tr><td colspan="5" class="text-center text-muted">데이터 없음</td></tr>');
            return;
        }
        const rows = items.map(item => `
            <tr>
                <td>${item.id}</td>
                <td>${item.sku}</td>
                <td>${item.name}</td>
                <td>${Number(item.price).toLocaleString()}원</td>
                <td>${item.quantity}</td>
            </tr>`);
        $tbody.html(rows.join(''));
    }

    $('#productForm').on('submit', function (e) {
        e.preventDefault();
        if (!ensureAuthOrLog()) { return; }
        const form = e.target;
        const payload = {
            sku: form.sku.value.trim(),
            name: form.name.value.trim(),
            description: form.description.value.trim(),
            price: Number(form.price.value),
            initialQuantity: Number(form.quantity.value)
        };
        const createUrl = '/api/v1/admin/products';
        ajaxJson('POST', createUrl, payload)
            .done(res => {
                logResponse('createProduct', res);
                $('#loadProductsBtn').trigger('click');
                form.reset();
            })
            .fail(jqXHR => handleError(jqXHR, { method: 'POST', url: createUrl }));
    });

    $('#stockForm').on('submit', function (e) {
        e.preventDefault();
        if (!ensureAuthOrLog()) { return; }
        const form = e.target;
        const productId = form.productId.value.trim();
        const payload = { quantityDelta: Number(form.delta.value) };
        const adjustUrl = `/api/v1/admin/products/${productId}/stock-adjust`;
        ajaxJson('POST', adjustUrl, payload)
            .done(res => {
                logResponse('adjustStock', res);
                $('#loadProductsBtn').trigger('click');
                form.reset();
            })
            .fail(jqXHR => handleError(jqXHR, { method: 'POST', url: adjustUrl }));
    });

    $('#regenKeyBtn').on('click', function () {
        $('#orderIdKey').val(randomKey());
    });

    $('#orderForm').on('submit', function (e) {
        e.preventDefault();
        if (!ensureAuthOrLog()) { return; }
        const form = e.target;
        const payload = {
            items: [{
                productId: Number(form.productId.value),
                quantity: Number(form.quantity.value)
            }]
        };
        const idKey = form.idempotencyKey.value.trim() || randomKey();
        const orderUrl = '/api/v1/orders';
        ajaxJson('POST', orderUrl, payload, { 'Idempotency-Key': idKey })
            .done(res => {
                logResponse('createOrder', res);
                $('#loadOrdersBtn').trigger('click');
                form.idempotencyKey.value = randomKey();
            })
            .fail(jqXHR => handleError(jqXHR, { method: 'POST', url: orderUrl }));
    });

    $('#loadOrdersBtn').on('click', function () {
        if (!ensureAuthOrLog()) { return; }
        const ordersUrl = '/api/v1/orders';
        ajaxJson('GET', ordersUrl)
            .done(res => {
                renderOrders(res.orders || []);
                logResponse('orders', res);
            })
            .fail(jqXHR => handleError(jqXHR, { method: 'GET', url: ordersUrl }));
    });

    function renderOrders(orders) {
        const $tbody = $('#ordersTable tbody');
        if (!orders.length) {
            $tbody.html('<tr><td colspan="4" class="text-center text-muted">데이터 없음</td></tr>');
            return;
        }
        const rows = orders.map(order => `
            <tr>
                <td>${order.id}</td>
                <td><span class="badge bg-dark">${order.status}</span></td>
                <td>${Number(order.totalAmount).toLocaleString()}원</td>
                <td>${order.idempotencyKey}</td>
            </tr>`);
        $tbody.html(rows.join(''));
    }

    // 초기 상태 설정
    $('#orderIdKey').val(randomKey());
    updateAuthDisplay();
    $('#loadProductsBtn').trigger('click');
})(jQuery);
