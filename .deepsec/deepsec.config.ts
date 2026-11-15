import { defineConfig, regexMatcher } from "deepsec/config";

export default defineConfig({
  plugins: [{
    name: "inshape-kotlin",
    matchers: [{
      slug: "inshape-kotlin-review",
      description: "Review production Kotlin, which the built-in language matchers do not select.",
      noiseTier: "noisy",
      filePatterns: ["app/src/main/**/*.kt"],
      examples: ["package com.emmah.inshape.transfer"],
      match(content) {
        return regexMatcher("inshape-kotlin-review", [{
          regex: /^package\s+com\.emmah\.inshape(?:\.\w+)*\s*$/gm,
          label: "Production Kotlin security review"
        }], content);
      }
    }]
  }],
  projects: [{
    id: "inshape",
    root: "..",
    promptAppend: "Review this offline Android workout journal. Prioritize backup import validation, archive and photo path confinement, bounded input processing, transaction rollback, health data permissions and sharing consent, exported Android components, FileProvider paths, and sensitive logging. Do not connect to personal devices or read personal health data.",
    priorityPaths: [
      "app/src/main/AndroidManifest.xml",
      "app/src/main/res/xml/",
      "app/src/main/java/com/emmah/inshape/transfer/",
      "app/src/main/java/com/emmah/inshape/media/",
      "app/src/main/java/com/emmah/inshape/health/"
    ]
  }]
});
