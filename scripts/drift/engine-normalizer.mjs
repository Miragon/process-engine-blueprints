export class EngineNormalizer {
  #spellings;

  constructor(engines) {
    this.#spellings = Object.values(engines ?? {}).flat().sort((left, right) => right.length - left.length);
  }

  normalize(text) {
    return this.#spellings.reduce((normalized, spelling) => normalized.replaceAll(spelling, '{engine}'), text);
  }
}
