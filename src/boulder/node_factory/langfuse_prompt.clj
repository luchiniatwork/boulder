(ns boulder.node-factory.langfuse-prompt
  (:require [boulder.prompt-template.core :as prompt]
            [boulder.prompt-template.langfuse :as langfuse-prompt]
            [me.pmatiello.openai-api.api :as openai]))


(defn create-node
  ([openai-config langfuse-server-config metadata prompt-id]
   (create-node openai-config langfuse-server-config metadata prompt-id nil))
  ([openai-config
    langfuse-server-config
    metadata
    prompt-id
    prompt-config]
   (let [node-prompt (langfuse-prompt/create langfuse-server-config prompt-id prompt-config)]
     (fn [input-map]
       (let [metadata' (merge (:metadata input-map) metadata)
             query-prompt (prompt/apply node-prompt input-map)]
         (cond
           (string? (:prompt query-prompt))
           (openai/completion (merge {:prompt (-> query-prompt :prompt)
                                      :metadata metadata'}
                                     (-> query-prompt :config :extra-body))
                              openai-config)
           (vector? (:prompt query-prompt))
           (openai/chat (merge {:messages (-> query-prompt :prompt)
                                :metadata metadata'}
                               (-> query-prompt :config :extra-body))
                        openai-config)))))))
