(ns rill.wheel.macro-utils)

(defn parse-args
  "provide defn-style doc-string suppport for defevent"
  [[sym & [doc-string? :as rst-args]]]
  (if (string? doc-string?)
    (into [(vary-meta sym assoc :doc doc-string?)] (drop 1 rst-args))
    (into [sym] rst-args)))

(defn keyword-in-current-ns
  [sym]
  (keyword (name (ns-name *ns*)) (name sym)))
