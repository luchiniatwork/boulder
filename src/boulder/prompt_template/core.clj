(ns boulder.prompt-template.core)

(defprotocol IPromptTemplate
  (apply [_ payload]))
