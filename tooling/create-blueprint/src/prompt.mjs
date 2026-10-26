import { createInterface } from 'node:readline/promises';
import { stdin, stdout } from 'node:process';

export function createPrompt() {
  const readline = createInterface({ input: stdin, output: stdout });

  async function choose(question, choices) {
    if (choices.length === 1) {
      return choices[0].value;
    }
    console.log(`\n${question}`);
    choices.forEach((choice, index) => console.log(`  ${index + 1}) ${choice.label}`));
    for (;;) {
      const answer = Number((await readline.question(`Choose 1-${choices.length}: `)).trim());
      if (Number.isInteger(answer) && answer >= 1 && answer <= choices.length) {
        return choices[answer - 1].value;
      }
    }
  }

  async function ask(question, fallback) {
    const answer = (await readline.question(`${question} (${fallback}): `)).trim();
    return answer || fallback;
  }

  return { choose, ask, close: () => readline.close() };
}
