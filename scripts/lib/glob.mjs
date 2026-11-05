function globToRegExp(glob) {
  let source = '';
  for (let index = 0; index < glob.length; index++) {
    const character = glob[index];
    if (glob.startsWith('**/', index)) {
      source += '(?:.*/)?';
      index += 2;
    } else if (glob.startsWith('**', index)) {
      source += '.*';
      index += 1;
    } else if (character === '*') {
      source += '[^/]*';
    } else {
      source += character.replace(/[.+?^${}()|[\]\\]/g, '\\$&');
    }
  }
  return new RegExp(`^${source}$`);
}

export function globMatcher(globs) {
  const included = globs.filter((glob) => !glob.startsWith('!')).map(globToRegExp);
  const excluded = globs.filter((glob) => glob.startsWith('!')).map((glob) => globToRegExp(glob.slice(1)));
  return (path) => included.some((pattern) => pattern.test(path)) && !excluded.some((pattern) => pattern.test(path));
}
