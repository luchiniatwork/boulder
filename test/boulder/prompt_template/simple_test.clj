(ns boulder.prompt-template.simple-test
  (:require [boulder.prompt-template.core :as prompt-core]
            [boulder.prompt-template.simple :as prompt-simple]
            [clojure.test :refer [testing is deftest]]))


(deftest simple-applies
  (let [prompt (prompt-simple/create {:user "foo {{bar}}"})]
    (is (= {:user "foo x"}
           (prompt-core/apply prompt {:bar "x"})))
    (is (= {:user "foo x"}
           (prompt-core/apply prompt {"bar" "x"})))
    (is (= {:user "foo {{bar}}"}
           (prompt-core/apply prompt {}))))
  (let [prompt (prompt-simple/create {:user "foo {{:bar/doo}}"})]
    (is (= {:user "foo x"}
           (prompt-core/apply prompt {:bar/doo "x"})))))


(deftest keys-to-apply
  (let [payload {:user "foo {{bar}}"
                 :config "foo {{bar}}"}
        prompt-basic (prompt-simple/create payload)
        prompt-keys (prompt-simple/create payload
                                          {:keys-to-apply [:user]})]
    (is (= {:user "foo x"
            :config "foo x"}
           (prompt-core/apply prompt-basic {:bar "x"})))
    (is (= {:user "foo x"
            :config "foo {{bar}}"}
           (prompt-core/apply prompt-keys {:bar "x"})))))
