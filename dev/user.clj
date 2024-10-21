(ns user
  (:require [boulder.prompt-template.core :as prompt-core]
            [boulder.prompt-template.simple :as prompt-simple]))

(let [prompt (prompt-simple/create {:user "foo {{bar}}"})]
  (prompt-core/apply prompt {:bar "x"}))

(let [prompt (prompt-simple/create {:user "foo {{bar}}"})]
  (prompt-core/apply prompt {"bar" "x"}))

(let [prompt (prompt-simple/create {:user "foo {{bar}}"})]
  (prompt-core/apply prompt {}))

(let [prompt (prompt-simple/create {:user "foo {{:bar/doo}}"})]
  (prompt-core/apply prompt {:bar/doo "x"}))
