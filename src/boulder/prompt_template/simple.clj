(ns boulder.prompt-template.simple
  (:require [boulder.prompt-template.core :as core]
            [clojure.string :as s]
            [clojure.walk :refer [postwalk]]))


(defn ^:private apply-template
  "In string template `<div>{:foo/bar}</div>`, replace all instances of $key$
  with target specified by map `m`. Target values are coerced to string with `str`.
  E.g. (template \"<div>{:foo}</div>\" {:foo 1}) => \"<div>1</div>\" - 1 is coerced to string."
  [t m] (reduce-kv (fn [acc k v] (s/replace acc (str "{" k "}") (str v))) t m))


(defn create [template-map]
  (reify core/IPromptTemplate
    (apply [_ payload-map]
      (postwalk (fn [x]
                  (cond-> x
                    (string? x)
                    ((fn [i] (apply-template i payload-map)))))
                template-map))))
