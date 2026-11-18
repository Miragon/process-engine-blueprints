import { posix } from 'node:path';
import { variantsOf } from './examples.mjs';

function variantDirs(examples, variant) {
  return examples
    .flatMap((example) => variantsOf(example).filter((entry) => entry.variant === variant).map(({ path }) => posix.join('examples', example.name, path)))
    .sort();
}

function project(components) {
  return `<?xml version="1.0" encoding="UTF-8"?>\n<project version="4">\n${components}</project>\n`;
}

export function renderIdeaMisc(examples) {
  const poms = variantDirs(examples, 'java-maven').map((dir) => `        <option value="$PROJECT_DIR$/${dir}/pom.xml" />\n`);
  return project(
    `  <component name="MavenProjectsManager">\n    <option name="originalFiles">\n      <list>\n${poms.join('')}      </list>\n    </option>\n  </component>\n`,
  );
}

export function renderIdeaGradle(examples) {
  const builds = variantDirs(examples, 'kotlin-gradle').map(
    (dir) => `      <GradleProjectSettings>\n        <option name="externalProjectPath" value="$PROJECT_DIR$/${dir}" />\n      </GradleProjectSettings>\n`,
  );
  return project(`  <component name="GradleSettings">\n    <option name="linkedExternalProjectsSettings">\n${builds.join('')}    </option>\n  </component>\n`);
}
