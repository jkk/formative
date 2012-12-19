# Formative

A Clojure library for dealing with web forms. Does three things:

* Turns form specifications into rendered forms via pluggable renderers. Comes with [Hiccup](https://github.com/weavejester/hiccup) renderers for Bootstrap (horizontal and stacked) and a table-based layout.
* Parses form data from Ring params
* Validates parsed data

## Installation

Leiningen coordinate:

```clj
[formative "0.1.0"]
```

## Usage


The important namespaces are `formative.core` and `formative.parse`.

```clj
(ns example.core
  (:require [formative.core :as f]
            [formative.parse :as fp]))
```

### Building a Form

To build a form, you need a form specification, which is a map that looks like this:

```clj
(def example-form
  {:method :post
   :action "/example/path"
   :submit-label "Do It"
   :cancel-href "/example"
   :fields [{:name :secret-code :type :hidden :datatype :int}
            {:name :email :type :email}
            {:name :password :type :password}
            {:name :remember :type :checkbox}]
   :validations [[:required [:secret-code :email :password]]
                 [:min-length 8 :password]]
   :values {:secret-code 1234
            :remember true}})
```

The map can contain keys such as `:method` and `:action` that directly correspond to HTML form attributes. It can also contain special keys such as `:fields` and `:submit-label`. See [Form and Field Specifications](#form-and-field-specifications) for more about the specification format.

### Rendering a Form

Render a form using `formative.core/render-form`:

```clj
(f/render-form example-form)
;; Returns:
[:div {:class "form-shell form-horizontal"}
 [:form {:method :post, :action "/example/path"}
  ;; ... etc ...
  ]]
```

Using the default `:bootstrap-horizontal` renderer and a [Bootstrap](http://twitter.github.com/bootstrap/) theme, the form will look something like this:

![schema](https://github.com/jkk/formative/raw/master/doc/bootstrap-horizontal.png)

Note: Formative does not include Bootstrap itself or any styling. You are responsible for providing CSS, images, etc.

### Parsing Form Data

`formative.parse/parse-params` will turn a form specification and a [Ring](https://github.com/ring-clojure/ring) `:form-params` or `:query-params` map into a map of parsed form data. It will parse each field according to its `:type` or `:datatype` keys.

```clj
(fp/parse-params example-form
                 {"secret-code" "1234"
                  "email" "foobar@gmail.com"
                  "password" "barbazquux"
                  "remember" "false"})
;; Returns:
{:remember false, :secret-code 1234, :password "barbazquux", :email "foobar@gmail.com"}
```

Note that the `:remember` and `:secret-code` fields have been parsed from strings into their respective datatypes.

By default, validation will be performed on the parsed values and an exception thrown if validation fails. If the `:validate` keyword option to `parse-params` is `false`, `ParseError` records will be used in place of values that failed to parse, and no validation will be done.

Any exception thrown due to a failed parse or validation will contain a `:problems` `ex-data` key with information about the failed exception.

```clj
(fp/parse-params example-form {"secret-code" "xxx"})
;; ExceptionInfo Problem parsing params  clojure.core/ex-info (core.clj:4227)

(ex-data *e)
;; {:problems ({:keys (:secret-code), :msg "must be an integer"}), …}

(fp/parse-params example-form {"secret-code" "xxx"} :validate false)
{:secret-code #formative.parse.ParseError{}}
```

### Validating Parsed Data

By default, only datatypes are validated. There are two ways to add your own validation to a form: `:validations` and `:validator`.

#### `:validations`

A sequence of validation specifications. For example:

```
[[:required [:foo :bar :password]]
 [:equal [:password confirm-password] "Passwords don't match, dummy"]
 [:min-length 8 :password]]
```

All validation specifications accept a key or sequence of keys. The message is always optional. Unless `:required` is used, all validations allow `nil` or blank.

Built in validations:

* `:required <keys> [msg]` - must not be blank or nil
* `:contains <keys> [msg]` - can be blank or nil but must be present in the values map
* `:exact <value> <keys> [msg]` - must be a particular value
* `:equal <keys> [msg]` - all keys must be equal
* `:email <keys> [msg]` - must be a valid email
* `:matches <regex> <keys> [msg]` - must match a regular expression
* `:min-length <len> <keys> [msg]` - must be a certain length (for strings or collections)
* `:max-length <len> <keys> [msg]` - must not exceed a certain length (for strings or collections)
* `:min-val <min> <keys> [msg]` - must be at least a certain value
* `:max-val <max> <keys> [msg]` - must be at most a certain value
* `:within <min> <max> <keys> [msg]` - must be within a certain range (inclusive)
* `:after <date> <keys> [msg]` - must be after a certain date
* `:before <date> <keys> [msg]` - must be before a certain date
* `:in <coll> <keys> [msg]` - must be contained within a collection
* `:us-zip <keys> [msg]` - must be a valid US zip code
* `:us-state <keys> [msg]` - must be a valid two-letter US state code
* `:ca-state <keys> [msg]` - must be a valid two-letter Canadian province code
* `:country <keys> [msg]` - must be a valid ISO alpha2 country code
* Data type validations: `:boolean`, `:integer`, `:float`, `:decimal`, `:date`
* Data type collection validations: `:booleans`, `:integers`, `:floats`, `:decimals`, `:dates`

All validation specifications have corresponding validator functions in the `formative.validate` namespace.

#### `:validator`

A function that takes a map of parsed values and returns a problem map or sequence of problem maps. A problem map has the keys `:keys`, indicating which keys were problems, and `:msg`, a description of what's wrong. If `nil` or an empty sequence is returned, validation succeeds. 

```clj
(defn validate-password [values]
  (when (#{"12345" "password" "hunter2"} (:password values))
    {:keys [:password] :msg "You can't use that password"})))
```

## Form and Field Specifications

Valid keys for a form specification include the following HTML form attributes:

      :action :method :enctype :accept :name :id :class
      :onsubmit :onreset :accept-charset :autofill

And the following special keys:

      :renderer     - Determines the type of renderer to use. Built-in options:
                        :bootstrap-horizontal (the default)
                        :bootstrap-stacked
                        :table
                        :inline
      :fields       - Sequence of form field specifications. See below.
      :values       - Map of values used to populate the form fields
      :submit-label - Label to use on the submit button. Defaults to "Submit"
      :cancel-href  - When provided, shows a "Cancel" link or button next to the
                      submit button
      :validations  - A sequence of validation specifications
      :validator    - A function to call to validate parsed values for this
                      form. The function should take a map of values and return
                      a sequence of problem maps for each field that failed to
                      validate. The problem map should contain the keys :keys
                      and :msg.
      :problems     - Sequence of field names or problem maps. Form
                      renderers typically add a class and style to highlight
                      problem fields and, if problem maps are provided,
                      show descriptive messages.
                      
New form renderers can be implemented using the `formative.render-form/render-form*` multimethod.

A field specification is a map with the following keys:

      :name         - Required name of the field, a keyword
      :type         - UI type of the field. Defaults to :text. Built-in types
                      include: :text, :textarea, :select, :checkbox,
                      :checkboxes, :radio, :html, :heading, :us-state,
                      :ca-state, :country, :date-select. Each type may have particular
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
                      :str, :int, :long, :boolean, :float, :double, :decimal,
                      :bigint, :date, :file. Defaults to :str.

                      All types can be appended with an "s" when a sequence
                      is expected - e.g., :ints for a sequence of integers. This
                      is useful for fields that have composite values, such as
                      :checkboxes.

                      :date field values are expected to be in yyyy-MM-dd
                      format by default. Set :date-format to change that.

                      :file fields must have an :upload-handler key which is
                      a function that takes two arguments: the field
                      specification, and the Ring file upload payload.

Field types are extensible with the `formative.render-field/render-field` and `formative.parse/parse-input` multimethods.

## Fuller Example

```clj
(ns example.core
  (:require [formative.core :as f]
            [formative.parse :as fp]
            [hiccup.core :as hiccup]
            [compojure.core :refer [defroutes GET POST]]))

(def example-form
  {:method :post
   :fields [{:name :full-name}
            {:name :email :type :email}
            {:name :spam :type :checkbox :label "Yes, please spam me."}
            {:name :password :type :password}
            {:name :password-confirm :type :password}
            {:name :flavors :type :checkboxes
             :options ["Chocolate" "Vanilla" "Strawberry" "Mint"]}]
   :validations [[:required [:full-name :email :password]]
                 [:min-length 8 :password]
                 [:equal [:password :password-confirm]]
                 [:min-length 2 :flavors "Please select two or more flavors"]]})

(defn show-example-form [req & {:keys [problems]}]
  (let [defaults {:spam true}]
    (hiccup/html
      [:h1 "Example"]
      (f/render-form (assoc example-form
                            :values (merge defaults
                                           (:params req))
                            :problems problems)))))

(defn submit-example-form [req]
  (fp/with-fallback req show-example-form
    (let [values (fp/parse-request example-form req)]
      (str "<p>Thank you!</p><pre>" (prn-str values) "</pre>"))))

(defroutes example-routes
  (GET "/example" _ show-example-form)
  (POST "/example" _ submit-example-form))

```

## License

Copyright © 2012 Justin Kramer

Distributed under the Eclipse Public License, the same as Clojure.
