import * as esbuild from "esbuild"
import shared from "./build_config.mjs"

await esbuild.build({
  ...shared,
  minify: true,
  entryNames: "[name]",
})
