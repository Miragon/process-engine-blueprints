export class DriftRule {
  check(context) {
    throw new Error(`${this.constructor.name} must implement check(context)`);
  }
}
