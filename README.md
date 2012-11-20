# formative

A Clojure library for generating [Hiccup](https://github.com/weavejester/hiccup) forms and parsing submitted form data.

## Installation

Leiningen coordinate:

```clj
[formative "0.1.0"]
```

## Usage


The important namespaces are `formative.core` and `formative.parse`:

```clj
(ns example.core
  (:require [formative.core :as f]
            [formative.parse :as fp]))
```

### Building a Form

To build a form, you need a form specification, which is a map that looks like this:

```clj
(def test-form
  {:method :post
   :action "/example/path"
   :submit-label "Do It"
   :cancel-href "/example"
   :fields [{:name :secret-code :type :hidden :datatype :int}
            {:name :email :type :email}
            {:name :password :type :password}
            {:name :remember :type :checkbox}]
   :values {:secret-code 1234
            :remember true}})
```

The map can contain keys such as `:method` and `:action` that directly correspond to HTML form attributes. It can also contain special keys such as `:fields` and `:submit-label`. See [Form and Field Specifications](#form-specifications) for more about the specification format.

### Rendering a Form

You can render the form as Hiccup data using `formative.core/render-form`:

```clj
(f/render-form example-form)
;; Returns:
[:div {:class "form-shell form-horizontal"}
 [:form {:method :post, :action "/example/path"}
  (([:input {:value 1234, :name "secret-code", :type :hidden}])
    [:fieldset
     ([:div {:id "row-field-email",
             :class "control-group field-group email-row"}
       ([:div {:class "label-shell"}
         [:label.control-label {:for "field-email"} "Email"]]
         [:div.input-shell.controls
          [:input {:value nil, :id "field-email", :name "email", :type :email}]])]
       ;; ...
       )])]]
```

Using the default `:bootstrap-horizontal` renderer and a [Bootstrap](http://twitter.github.com/bootstrap/) theme, the form will look something like this:

![schema](https://github.com/jkk/formative/raw/master/doc/bootstrap-horizontal.png)

Note: Formative does not include Bootstrap itself or any styling. You are responsible for providing CSS, images, etc.

### Parsing Form Data

`formative.parse/parse-params` will turn a form specification and a [Ring](https://github.com/ring-clojure/ring) param map into a map of parsed form data. It will parse each field according to its `:type` and `:datatype` keys.

```clj
(fp/parse-params
  (:fields example-form)
  {"secret-code" "1234"
   "email" "foobar@gmail.com"
   "password" "bazquux"
   "remember" "false"})
;; => {:remember false, :secret-code 1234, :password "bazquux", :email "foobar@gmail.com"}
```

You'll see that the `:remember` and `:secret-code` fields have been parsed from strings into their respective datatypes.

## Form and Field Specifications

Valid keys for a form specification include the following HTML form attributes:

      :action :method :enctype :accept :name :id :class
      :onsubmit :onreset :accept-charset :autofill

And the following special keys:

      :type         - Determines the type of renderer to use. Built-in options:
                        :bootstrap-horizontal (the default)
                        :bootstrap-stacked
                        :table
      :fields       - Sequence of form field specifications. See below.
      :values       - Map of values used to populate the form fields
      :submit-label - Label to use on the submit button. Defaults to "Submit"
      :cancel-href  - When provided, shows a "Cancel" link or button next to the
                      submit button
      :problems     - Sequence of field names that are a "problem" (e.g., incorrect
                      format). Form renderers typically add a class and style to
                      highlight problem fields.
                      
New form renderers can be implemented using the `formative.render-form/render-form*` multimethod.

A field specification is a map with the following keys:

      :name         - Required name of the field, a keyword
      :type         - UI type of the field. Defaults to :text. Built-in types
                      include: :text, :textarea, :select, :checkbox,
                      :checkboxes, :radio, :html, :heading, :us-state,
                      :ca-state, :country. Each type may have particular
                      keys that it makes use of.

                      Selection fields such as :select, :checkboxes, and :radio
                      expect an :options key, which is a collection of options
                      which conform to one of the following formats:
                        - ["value" "label"]
                        - {:value "value" :label "label"]
                        - "value and label"

                      The :heading type expects a :text key, a string or Hiccup data.

                      The :html type expects an :html key, a string or Hiccup data.
      :datatype     - Datatype of the field used for parsing. Can be one of:
                      :int, :long, :boolean, :float, :double, :decimal, :bigint,
                      :date, :file.

                      All types can be appended with an "s" when a sequence
                      is expected - e.g., :ints for a sequence of integers. This
                      is useful for fields that have composite values, such as
                      :checkboxes.

                      :date field values are expected to be in YYYY-MM-DD
                      format.

                      :file fields must have an :upload-handler key which is
                      a function that takes two arguments: the field
                      specification, and the Ring file upload payload.

Field types are extensible with the `formative.render-field/render-field` and `formative.parse/parse-input` multimethods.

## License

Copyright © 2012 Justin Kramer

Distributed under the Eclipse Public License, the same as Clojure.
