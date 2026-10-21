// Tracks which lessons a learner has finished. Everything stays in this
// browser's localStorage; nothing is sent anywhere.
(() => {
	const KEY = 'fsfz-progress-v1';
	const norm = (path) => path.replace(/\/+$/, '') || '/';

	const load = () => {
		try {
			const saved = JSON.parse(localStorage.getItem(KEY) || '{}');
			return saved && typeof saved === 'object' ? saved : {};
		} catch {
			return {};
		}
	};
	const save = (done) => {
		try {
			localStorage.setItem(KEY, JSON.stringify(done));
		} catch {
			// Private windows can block storage; progress just won't persist.
		}
	};

	const markSidebar = (done) => {
		document.querySelectorAll('nav.sidebar a[href]').forEach((link) => {
			const path = norm(new URL(link.href, location.href).pathname);
			link.classList.toggle('is-done', Boolean(done[path]));
		});
	};

	const wireButtons = (done) => {
		const here = norm(location.pathname);
		document.querySelectorAll('[data-complete-button]').forEach((button) => {
			const render = () => {
				const isDone = Boolean(done[here]);
				button.setAttribute('aria-pressed', String(isDone));
				button.textContent = isDone ? '✓ Finished. Click to undo' : 'Mark this lesson as finished';
			};
			button.addEventListener('click', () => {
				if (done[here]) delete done[here];
				else done[here] = { title: document.title.split('|')[0].trim(), at: Date.now() };
				save(done);
				render();
				markSidebar(done);
			});
			render();
		});
	};

	const renderProgressPage = (done) => {
		const list = document.querySelector('[data-progress-list]');
		if (!list) return;
		const entries = Object.entries(done).sort((a, b) => a[0].localeCompare(b[0]));
		const count = document.querySelector('[data-progress-count]');
		if (count) count.textContent = String(entries.length);
		list.replaceChildren(
			...entries.map(([path, info]) => {
				const item = document.createElement('li');
				const link = document.createElement('a');
				link.href = path;
				link.textContent = info.title || path;
				item.append(link);
				return item;
			})
		);
		const reset = document.querySelector('[data-progress-reset]');
		reset?.addEventListener('click', () => {
			save({});
			renderProgressPage({});
			markSidebar({});
		});
	};

	const start = () => {
		const done = load();
		markSidebar(done);
		wireButtons(done);
		renderProgressPage(done);
	};

	if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', start);
	else start();
})();
