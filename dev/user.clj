(ns user
  (:require [boulder.prompt-template.core :as prompt-core]
            [boulder.prompt-template.simple :as prompt-simple]))

(let [prompt (prompt-simple/create {:foo "bar {:var}"})]
  (prompt-core/apply prompt {:var "x"}))
