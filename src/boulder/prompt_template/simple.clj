(ns boulder.prompt-template.simple
  (:require [boulder.prompt-template.core :as core]
            [clojure.string :as s]
            [clojure.walk :refer [postwalk]]))


(defn ^:private apply-template
  [t m]
  (reduce-kv (fn [acc k v]
               (-> acc
                   (s/replace (str "{{" k "}}") (str v))
                   (s/replace (str "{{" (name k) "}}") (str v))))
             t m))


(defn create
  ([template-map]
   (create template-map nil))
  ([template-map {:keys [keys-to-apply] :as config}]
   (reify core/IPromptTemplate
     (apply [_ payload-map]
       (let [staged (if keys-to-apply
                      (select-keys template-map keys-to-apply)
                      template-map)]
         (merge template-map
                (postwalk (fn [x]
                            (cond-> x
                              (string? x)
                              ((fn [i] (apply-template i payload-map)))))
                          staged)))))))
