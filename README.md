# Full-Stack From Zero

A free, self-study course that takes complete beginners from never having written code to shipping **PayDeck**, a card and payments app built with React Native (Android and macOS) and a Java 25 Spring Boot backend.

## What is in this repo

| Folder | What it holds |
| --- | --- |
| `site/` | The course website, built with [Astro Starlight](https://starlight.astro.build/) |
| `api/` | The PayDeck backend: Java 25 and Spring Boot 4. See [api/README.md](api/README.md) |
| `app/` | The PayDeck app: React Native for Android and macOS. See [app/README.md](app/README.md) |
| `.github/workflows/` | Publishes the website, and tests and builds the API and the app on every change |

## Working on the website

You need Node.js 22 or newer.

```sh
cd site
npm install
npm run dev      # http://localhost:4321
npm run build    # production build in site/dist
```

- The course outline (parts, modules, outcomes, mini-projects) lives in `site/src/data/curriculum.mjs`. The sidebar and the course map are built from it.
- `node scripts/create-module-pages.mjs` (run inside `site/`) creates an overview page for any module that does not have one yet.
- Lessons go in each module's folder under `site/src/content/docs/`. Start from `site/src/content/docs/_lesson-template.mdx`.
- Lesson building blocks live in `site/src/components/`: `Exercise` (task with hints and a solution), `Quiz` (checkpoint quiz with explanations) and `MarkComplete` (progress tracking, stored in the learner's browser).

## Publishing

In the repo's **Settings → Pages**, set **Source** to **GitHub Actions**. After that, every push to `main` that changes `site/` rebuilds and publishes the website.
