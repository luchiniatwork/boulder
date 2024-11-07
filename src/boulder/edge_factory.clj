(ns boulder.edge-factory
  (:require [jsonista.core :as json]))

(defn base-llm-extractor-transform [input-map]
  (let [choice (first (:choices input-map))]
    (or (:text choice)
        (:message choice))))

(defn message-llm-extractor-transform [input-map]
  (-> input-map
      base-llm-extractor-transform
      :content))

(defn base-llm-json-extractor-transform [input-map]
  (-> input-map
      base-llm-extractor-transform
      (json/read-value json/keyword-keys-object-mapper)))

(defn message-llm-json-extractor-transform [input-map]
  (-> input-map
      message-llm-extractor-transform
      (json/read-value json/keyword-keys-object-mapper)))
