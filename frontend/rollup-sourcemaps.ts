import type {ExistingRawSourceMap, Plugin} from "rollup"
import {createFilter} from "@rollup/pluginutils"
import * as fs from "fs";

export interface SourcemapOptions {
  include?: string
  exclude?: string
  enabled?: boolean
}

export function defaultSourcemapFix(path: string) {
  // relativeSourcePath is unreliable; this replaces bogus paths with absolute paths
  const httpsFixed = sourcemapFix(path, "https:/", "https://")
  return sourcemapFix(httpsFixed, "file:/", "file:///")
}

// With a path of "../src/https:/www.google.com", prefix of "https:/" and replacement of "https://",
// returns "https://www.google.com"
export function sourcemapFix(path: string, prefix: string, replacement: string): string {
  const idx = path.indexOf(prefix)
  if (idx > 0) {
    const address = path.substring(idx + prefix.length)
    return `${replacement}${address}`
  }
  return path
}

async function fileExists(path: string): Promise<boolean> {
  try {
    await fs.promises.stat(path)
    return true
  } catch (e) {
    return false
  }
}

export function sourcemaps(options: SourcemapOptions): Plugin {
  const filter = createFilter(options.include || "**/main.js", options.exclude)
  return {
    name: "rollup-plugin-sourcemaps",
    async load(id: string) {
      if (!filter(id) || options.enabled === false) return
      const mapFile = `${id}.map`
      if (!(await fileExists(mapFile))) {
        return
      }
      const code = await fs.promises.readFile(id, "utf-8")
      const sourcemap = await fs.promises.readFile(mapFile, "utf-8")
      const existing: ExistingRawSourceMap = JSON.parse(sourcemap)
      return { code: code, map: existing }
    }
  }
}

export default sourcemaps
