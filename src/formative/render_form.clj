(ns formative.render-form)

(defmulti render-form*
  "Render a form, dispatching on :renderer.

  form-attrs - HTML attributes of the form, plus a :renderer key
  fields     - normalized, prepared fields to include in the form
  opts       - the full form specification with all keys untouched"
  (fn [form-attrs fields opts]
    (:renderer form-attrs)))