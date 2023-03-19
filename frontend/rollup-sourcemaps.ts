import type {ExistingRawSourceMap, Plugin} from "rollup"
import {createFilter} from "@rollup/pluginutils"
import * as fs from "fs";

export interface SourcemapOptions {
  include?: string
  exclude?: string
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

export function sourcemaps(options: SourcemapOptions): Plugin {
  const filter = createFilter(options.include || "**/main.js", options.exclude)
  return {
    name: "rollup-plugin-sourcemaps",
    async load(id: string) {
      if (!filter(id)) return
      const mapFile = `${id}.map`
      try {
        await fs.promises.stat(mapFile)
      } catch (e) {
        return
      }
      const code = await fs.promises.readFile(id, "utf-8")
      const sourcemap = await fs.promises.readFile(mapFile, "utf-8")
      console.log(`Sourcemap of ${id}`)
      const existing: ExistingRawSourceMap = JSON.parse(sourcemap)
      return { code: code, map: existing }
    }
  }
}

export default sourcemaps

