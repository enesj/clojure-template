/**
 * Optimized WarpGrep Configuration for clojure-template
 *
 * This configuration provides high-performance code search with project-specific
 * exclusions to reduce noise and improve relevance of search results.
 *
 * Usage:
 *   const { createWarpGrepTool } = await import('@morphllm/morphmcp');
 *   const grepTool = createOptimizedWarpGrep();
 */

const { createWarpGrepTool } = require('@morphllm/morphmcp');

/**
 * Project-specific exclusion patterns for cleaner search results
 * These directories and files contain noise, dependencies, or generated content
 * that typically don't need to be searched during development.
 */
const PROJECT_EXCLUDES = [
  // Version control and Git metadata
  ".git",
  ".gitignore",
  ".gitattributes",

  // Node.js dependencies and package manager artifacts
  "node_modules",
  "npm-debug.log*",
  "yarn-debug.log*",
  "yarn-error.log*",
  "package-lock.json",
  "yarn.lock",

  // Clojure/ClojureScript build artifacts and compilation outputs
  "target",
  ".shadow-cljs",
  "classes",
  ".cpcache",

  // Linting and analysis caches
  ".clj-kondo",
  ".clj-kondo/*",

  // JavaScript/TypeScript build outputs and distributions
  "dist",
  "build",
  "out",
  ".next",

  // IDE and editor files
  ".vscode",
  ".idea",
  "*.swp",
  "*.swo",
  "*~",

  // OS-specific files
  ".DS_Store",
  "Thumbs.db",

  // Temporary and cache files
  "*.tmp",
  "*.temp",
  ".cache",

  // Documentation and generated files that typically don't need searching
  "docs/api",
  "*.generated.*"
];

/**
 * Creates an optimized WarpGrep tool configured for this specific project
 */
function createOptimizedWarpGrep() {
  return createWarpGrepTool({
    repoRoot: '.',
    morphApiKey: process.env.MORPH_API_KEY,
    remoteCommands: {
      excludes: PROJECT_EXCLUDES
    }
  });
}

/**
 * Alternative configuration with additional file type filters
 * Useful when you want to search only specific file types
 */
function createFileTypeSpecificWarpGrep(fileExtensions = ['.clj', '.cljs', '.cljc', '.edn']) {
  return createWarpGrepTool({
    repoRoot: '.',
    morphApiKey: process.env.MORPH_API_KEY,
    remoteCommands: {
      excludes: PROJECT_EXCLUDES,
      includePatterns: fileExtensions.map(ext => `*${ext}`)
    }
  });
}

/**
 * Configuration for searching only source code (excluding tests and docs)
 */
function createSourceCodeWarpGrep() {
  const sourceOnlyExcludes = [
    ...PROJECT_EXCLUDES,
    "test/**",
    "tests/**",
    "spec/**",
    "docs/**",
    "*.md",
    "*.txt"
  ];

  return createWarpGrepTool({
    repoRoot: '.',
    morphApiKey: process.env.MORPH_API_KEY,
    remoteCommands: {
      excludes: sourceOnlyExcludes
    }
  });
}

module.exports = {
  createOptimizedWarpGrep,
  createFileTypeSpecificWarpGrep,
  createSourceCodeWarpGrep,
  PROJECT_EXCLUDES
};
