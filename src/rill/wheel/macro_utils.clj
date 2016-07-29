(ns rill.wheel.macro-utils)

(defn parse-doc-string
  "provide defn-style doc-string suppport for def* macros"
  [[sym & [doc-string? :as rst-args]]]
  (if (string? doc-string?)
    (into [(vary-meta sym assoc :doc doc-string?)] (drop 1 rst-args))
    (into [sym] rst-args)))

(defn parse-attr-map
  [[sym & [attr-map? :as rst-args]]]
  (if (map? attr-map?)
    (into [(vary-meta sym merge attr-map?)] (drop 1 rst-args))
    (into [sym] rst-args)))

(defn parse-args
  [args]
  (-> args
      parse-doc-string
      parse-attr-map))

(defn keyword-in-current-ns
  [sym]
  (keyword (name (ns-name *ns*)) (name sym)))
