// @ts-check
import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';
import { parts } from './src/data/curriculum.mjs';

// SITE_URL and BASE_PATH are set by the GitHub Pages workflow, so the site
// works both locally (served from /) and from a project page (/<repo-name>/).
const site = process.env.SITE_URL;
const base = process.env.BASE_PATH || '/';

// One sidebar group per part, with one collapsible group per module inside it.
const partGroups = parts.map((part) => ({
	label: `Part ${part.n}: ${part.title}`,
	collapsed: true,
	items: part.modules.map((module) => ({
		label: `${module.n}. ${module.title}`,
		collapsed: true,
		items: [{ autogenerate: { directory: `${part.dir}/${module.dir}` } }],
	})),
}));

export default defineConfig({
	site,
	base,
	integrations: [
		starlight({
			title: 'Full-Stack From Zero',
			description:
				'A free, self-study course that takes you from never having coded to shipping a card and payments app with React Native and Java 25 Spring Boot.',
			customCss: ['./src/styles/custom.css'],
			head: [
				{
					tag: 'script',
					attrs: { src: `${base.replace(/\/$/, '')}/progress.js`, defer: true },
				},
			],
			sidebar: [
				{ label: 'Start here', items: [{ autogenerate: { directory: 'start-here' } }] },
				...partGroups,
				{ label: 'Reference', items: [{ autogenerate: { directory: 'reference' } }] },
			],
		}),
	],
});
