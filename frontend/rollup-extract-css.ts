import type {Plugin} from "rollup"
import {createFilter} from "@rollup/pluginutils"
import autoprefixer from "autoprefixer"
import cssnano from "cssnano"
import path from "path"
import postcss, {SourceMap} from "postcss"
import postcssUrl from "postcss-url"
import postcssNesting from "postcss-nesting"

// Inspiration from https://github.com/egoist/rollup-plugin-postcss/blob/master/src/index.js

function importOrder(id, getInfo): string[] {
  return getInfo(id).importedIds.flatMap(imported => {
    return [imported].concat(importOrder(imported, getInfo))
  }).filter((v, idx, arr) => arr.indexOf(v) === idx)
}

export interface ExtractOptions {
  outDir: string
  include?: string
  exclude?: string
  minimize?: boolean
  urlOptions: any
  sourceMap?: boolean
}

export default function extractcss(options: ExtractOptions): Plugin {
  const filter = createFilter(options.include || "**/*.css", options.exclude)
  const minimize = options.minimize || false
  const useSourceMap = options.sourceMap === true
  const basicPlugins = [postcssNesting(), autoprefixer, postcssUrl(options.urlOptions)]
  const extraPlugins = minimize ? [cssnano()] : []
  const plugins = basicPlugins.concat(extraPlugins)
  const processed = new Map<string, string>()
  let sourceMap: SourceMap | null = null
  return {
    name: "rollup-plugin-extract-css",
    async transform(code, id) {
      if (!filter(id)) return
      const result = await postcss(plugins)
        .process(code, {from: id, to: path.resolve(options.outDir, "frontend.css"), map: useSourceMap})
      if (useSourceMap) {
        sourceMap = result.map
      }
      processed.set(id, result.css)
      return {code: "", map: undefined}
    },
    augmentChunkHash(chunkInfo) {
      // JSON stringifies a Map. Go JavaScript.
      const ids = importOrder(chunkInfo.facadeModuleId, this.getModuleInfo)
      const obj = Array.from(processed).reduce((obj, [key, value]) => {
        if (ids.includes(key)) {
          obj[key] = value
        }
        return obj
      }, {})
      return JSON.stringify(obj)
    },
    async generateBundle(opts, bundle) {
      if (processed.size === 0) return
      Object.keys(bundle).forEach(entry => {
        const b = bundle[entry]
        if (b.type == "chunk" && b.isEntry) {
          const facade = b.facadeModuleId
          const orderedIds = importOrder(facade, this.getModuleInfo)
          const contents = orderedIds.map(id => processed.get(id)).filter(c => c)
          const content = "".concat(...contents)
          const name = path.parse(entry).name
          const fileName = `${name}.css`
          const ref = this.emitFile({
            name: b.name,
            fileName: fileName,
            type: "asset",
            source: content
          })
          console.log(`Generated ${this.getFileName(ref)} from ${b.name}`)
          if (sourceMap && useSourceMap) {
            const mapRef = this.emitFile({
              fileName: `${fileName}.map`,
              type: "asset",
              source: sourceMap.toString()
            })
            console.log(`Generated ${this.getFileName(mapRef)} from ${b.name}`)
          }
        }
      })
    }
  }
}
