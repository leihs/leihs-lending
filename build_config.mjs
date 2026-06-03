export default {
  bundle: true,
  sourcemap: true,
  entryPoints: ["dev/gen/libs.js"],
  outdir: "resources/public/lending/assets/js",

  resolveExtensions: [".js", ".jsx", ".ts", ".tsx"],
  alias: {
    src: "./src",
    "@/*": "./src/leihs/lending/client/*",
    "@@/*": "./src/leihs/lending/client/components/ui/*",
  },

  define: {
    "process.env.NODE_ENV": `"${process.env.NODE_ENV}"`,
  },

  target: ["es2022", "chrome119", "firefox120", "safari17", "edge119"],
  metafile: true,
  logLevel: "info",
}
