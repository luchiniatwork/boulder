(ns boulder.prompt-template.langfuse
  (:require [boulder.prompt-template.core :as core]
            [boulder.prompt-template.simple :as simple]
            [clj-http.client :as http]
            [cljc.java-time.instant :as inst]
            [jsonista.core :as json]))


(defn ^:private fetch-prompt [{:keys [url public-key secret-key] :as _server-config}
                              {:keys [label]
                               :or {label "production"}
                               :as _prompt-config}
                              prompt-id]
  (let [{:keys [status body]} (-> (str url "/api/public/v2/prompts/" prompt-id)
                                  (http/get {:accept :json
                                             :basic-auth [public-key secret-key]
                                             :query-params {:label label}}))]
    (when (= 200 status)
      (-> body
          (json/read-value json/keyword-keys-object-mapper)))))


(defn ^:private update-atom! [!a
                              server-config
                              {:keys [keys-to-apply]
                               :or {keys-to-apply [:prompt]}
                               :as prompt-config}
                              prompt-id]
  (try
    (let [{:keys [prompt config]
           :as raw-prompt} (fetch-prompt server-config prompt-config prompt-id)]
      (when (not (nil? raw-prompt))
        (reset! !a {:timestamp (inst-ms (inst/now))
                    :raw-prompt raw-prompt
                    :simple-downstream (simple/create {:prompt prompt
                                                       :config config}
                                                      {:keys-to-apply keys-to-apply})})))
    (catch Throwable ex
      (when (empty? @!a)
        (throw ex)))))


(defn create
  ([server-config prompt-id]
   (create server-config prompt-id nil))
  ([{:keys [url public-key secret-key]
     :as server-config}
    prompt-id
    {:keys [label lazy-init? cache-time-ms]
     :or {lazy-init? false
          cache-time-ms 300000}
     :as prompt-config}]
   (let [!prompt (atom {})]
     (when (not lazy-init?)
       (update-atom! !prompt server-config prompt-config prompt-id))
     (reify core/IPromptTemplate
       (apply [_ payload-map]
         (when (or (empty? @!prompt)
                   (> (inst-ms (inst/now))
                      (+ cache-time-ms (:timestamp @!prompt))))
           (update-atom! !prompt server-config prompt-config prompt-id))
         (if (not (empty !prompt))
           (core/apply (:simple-downstream @!prompt) payload-map)
           (throw (ex-info "Unable to load langfuse prompt" {:prompt-id prompt-id}))))))))


#_(let [url #_"https://langfuse.theoai.ai"
        "http://theo-langfuse.internal:7000"
        prompt-id "facts-extractor" #_"score-correctness"
        full-url (str url "/api/public/v2/prompts/" prompt-id)
        public-key "pk-lf-81417b46-ef1d-4368-acc6-58232b2c0168"
        secret-key "sk-lf-bc7914bd-eb19-47db-befc-4eacb7d6723e"
        label "production"

        server-config {:url url
                       :public-key public-key
                       :secret-key secret-key}
        prompt-config {:label label}]
    (fetch-prompt server-config prompt-config prompt-id)
    #_(core/apply (create server-config
                          prompt-id
                          {:keys-to-apply [:prompt :config]})
                  {:claim_content "FOOBAR!!!!!"
                   :var "xxxx"}))
