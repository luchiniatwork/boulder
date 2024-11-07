(ns user
  (:require [boulder.edge-factory :as edge]
            [boulder.node-factory.langfuse-prompt :as prompt-node]
            [boulder.node-factory.langfuse-score :as score-node]
            [boulder.ring-adapter :refer [create-simple-handler]]
            [clojure.string :as s]
            [dag-o-bert.core :as dag]
            [me.pmatiello.openai-api.api :as openai]))

(def openai-config (openai/config :api-key ""
                                  :base-url "http://theo-litellm-dev.flycast"))

(def langfuse-config {:url "https://langfuse.theoai.ai"
                      :public-key ""
                      :secret-key ""})


(defn ^:private nano-id []
  (let [f (fn [c start]
            (->> (range 0 c)
                 (map #(+ start %))
                 (map char)
                 (apply str)))
        dict (str (f 26 65) ;; [A-Z]
                  (f 26 97) ;; [a-z]
                  (f 10 48) ;; [0-9]
                  "-_")]
    (->> (range 0 21)
         (map (fn [_] (rand-nth dict)))
         (apply str))))

(comment
  (def score-correctness (prompt-node/create-node openai-config
                                                  langfuse-config
                                                  {:generation_name "score-correctness"}
                                                  "score-correctness"))

  (def clojure-infer (prompt-node/create-node openai-config
                                              langfuse-config
                                              {:generation_name "clojure-infer"}
                                              "clojure-infer"))

  (def eval-proper (fn [{:keys [expr]}]
                     (let [result (-> expr read-string eval)
                           t (type result)]
                       (->> ["The correct Clojure evaluation"
                             (str "for expression `" expr "` is")
                             (str "`" result "`")
                             "of type" (str t)]
                            (s/join " ")))))

  (def post-score (score-node/create-node langfuse-config {:data-type :numeric
                                                           :name "correctness"}))

  (def graph {:nodes {:start identity
                      :clojure-infer clojure-infer
                      :score-correctness score-correctness
                      :eval-proper eval-proper
                      :post-score post-score
                      :println #(do (clojure.pprint/pprint %)
                                    %)
                      :end identity}
              :edges [[:start :eval-proper {:skip-name? true}]

                      [:start :clojure-infer {:skip-name? true}]

                      [:start :score-correctness
                       {:skip-name? true
                        :transform #(-> %
                                        (assoc :query (:expr %))
                                        (dissoc :expr))}]

                      [:eval-proper :score-correctness
                       {:name :ground_truth}]

                      [:clojure-infer :score-correctness
                       {:name :generation
                        :transform #(-> %
                                        edge/message-llm-json-extractor-transform
                                        ((fn [{:keys [result reasoning type]}]
                                           (str "Result is `" result "` "
                                                " of type `" type "`. Reasoning: "
                                                reasoning))))}]

                      [:score-correctness :println
                       {:transform edge/base-llm-json-extractor-transform}]

                      [:clojure-infer :println
                       {:transform edge/message-llm-json-extractor-transform}]

                      [:start :post-score {:skip-name? true
                                           :transform (fn [{:keys [metadata]}]
                                                        {:trace-id (:trace_id metadata)})}]

                      [:score-correctness :post-score
                       {:skip-name? true
                        :transform #(-> %
                                        edge/base-llm-json-extractor-transform
                                        ((fn [{:keys [score reasoning]}]
                                           {:value score
                                            :comment reasoning})))}]

                      [:println :end]]
              :start-node :start
              :end-node :end})

  #_(->> {:expr "(* 5 10 (/ 2 9))"
          :metadata {:trace_id (nano-id)
                     :trace_name "scoring-and-structured-output-test"}}
         (dag/run-sync graph {:observer #(println (:run-id %) (:node %))}))

  (let [agents {"my-agent" (fn [{:keys [expr]}]
                             (->> {:expr expr
                                   :metadata {:trace_id (nano-id)
                                              :trace_name "scoring-and-structured-output-test"}}
                                  (dag/run-sync graph)))}
        handler (create-simple-handler #(get agents %))]
    (handler {:request-method :post
              :headers {"accept" "application/json"}
              :query-params {:agent "my-agent"}
              :body-params {:expr "(* 5 10 (/ 2 9))"}}))
  
  )





(comment
  (def completion-node (prompt-node/create-node openai-config
                                                langfuse-config
                                                {:generation_name "completion-test"}
                                                "completion-test"))

  (def chat-node (prompt-node/create-node openai-config
                                          langfuse-config
                                          {:generation_name "chat-test"}
                                          "chat-test"))

  (def funny-eval-node (prompt-node/create-node openai-config
                                                langfuse-config
                                                {:generation_name "funny-expert-test"}
                                                "funny-expert-test"))

  (def graph {:nodes {:start identity
                      :completion completion-node
                      :chat chat-node
                      :println println
                      :eval funny-eval-node}
              :edges [[:start :completion {:skip-name? true}]
                      [:start :chat {:skip-name? true}]
                      [:completion :eval {:name :option_a
                                          :transform edge/base-llm-extractor-transform}]
                      [:chat :eval {:name :option_b
                                    :transform edge/message-llm-extractor-transform}]
                      [:start :eval {:skip-name? true}]]
              :start-node :start
              :end-node :eval})

  (->> {:topic "genetics"
        :role "pirate"
        :metadata {:trace_id (nano-id)
                   :trace_name "dag-test"}}
       (dag/run-sync graph)
       :choices
       first
       :message
       :content))


#_(completion-test {:topic "bears"})

#_(chat-node {:role "trained dog"
              :topic "election"})
