import * as esbuild from "esbuild"
import shared from "./build_config.mjs"

const ctx = await esbuild.context({ ...shared })
await ctx.watch()
