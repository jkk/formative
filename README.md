# Formative

A Clojure library for dealing with web forms. In particular, it can:

* Render a form via pluggable renderers (comes with Bootstrap and other renderers built in)
* Parse form data from Ring params
* Validate parsed data using [Verily](https://github.com/jkk/verily)

See a live demo at [formative-demo.herokuapp.com](http://formative-demo.herokuapp.com/) ([demo source](https://github.com/jkk/formative-demo))

## Installation

Leiningen coordinate:

```clj
[formative "0.3.1"]
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
  {:fields [{:name :secret-code :type :hidden :datatype :int}
            {:name :email :type :email}
            {:name :password :type :password}
            {:name :remember :type :checkbox}]
   :validations [[:required [:secret-code :email :password]]
                 [:min-length 8 :password]]
   :values {:secret-code 1234
            :remember true}})
```

The map can contain keys such as `:method` and `:action` that directly correspond to HTML form attributes (although unlike HTML, "post" is the default `:method`). It can also contain special keys such as `:fields` and `:submit-label`. See the [Form Specification](#form-specification) section for more about the specification format.

### Rendering a Form

Render a form using `formative.core/render-form`:

```clj
(f/render-form example-form)
;; Returns:
[:div {:class "form-shell form-horizontal bootstrap-form"}
 [:form {:method :post}
  ;; ... etc ...
  ]]
```

Using the default `:bootstrap-horizontal` renderer and a [Bootstrap](http://twitter.github.com/bootstrap/) theme, the form will look something like this:

![form](https://github.com/jkk/formative/raw/master/doc/bootstrap-horizontal.png)

Note: Formative does not include any CSS or images. You are responsible for providing those yourself.

Renderers are pluggable and can return any kind of format -- e.g., Hiccup or string. Formative comes with several built-in Hiccup renderers which you can set using the `:renderer` form specification key:

* `:bootstrap-horizontal` (the default)
* `:bootstrap-stacked`
* `:table`
* `:inline`

Custom form renderers can be implemented using the `formative.render/render-form` multimethod.

You can also render individual fields using `formative.core/render-field`. Unlike `render-form`, `render-field` _always_ returns Hiccup data. `render-field` takes a field specification and an optional value:

```clj
(f/render-field {:name :flavor
                 :type :select
                 :options ["Chocolate" "Vanilla" "Strawberry"]}
                "Vanilla")
;; Returns:
[:select {:name "flavor"}
 ([:option {:value "Chocolate", :selected false} "Chocolate"]
  [:option {:value "Vanilla", :selected true} "Vanilla"]
  [:option {:value "Strawberry", :selected false} "Strawberry"])]
```

Notice that the "Vanilla" option is selected in our generated element.

All of the built-in form renderers make use of `render-field`, but not all renderers are required to do so.

### Parsing Form Data

`formative.parse/parse-params` will turn a form specification and a [Ring](https://github.com/ring-clojure/ring) params map into a map of parsed form data. It will parse each field according to its `:type` or `:datatype` keys.

```clj
(fp/parse-params example-form
                 {:secret-code "1234"
                  :email "foobar@gmail.com"
                  :password "barbazquux"
                  :remember "false"})
;; Returns:
{:remember false, :secret-code 1234, :password "barbazquux", :email "foobar@gmail.com"}
```

Notice that the `:remember` and `:secret-code` fields have been parsed from strings into their respective datatypes.

By default, validation will be performed on the parsed values and an exception thrown if validation fails. If the `:validate` keyword option to `parse-params` is `false`, `ParseError` records will be used in place of values that failed to parse, and no validation will be done.

Any exception thrown due to a failed parse or validation will contain a `:problems` `ex-data` key with information about the failed exception.

```clj
(fp/parse-params example-form {:secret-code "xxx"})
;; ExceptionInfo Problem parsing params  clojure.core/ex-info (core.clj:4227)

(ex-data *e)
;; {:problems ({:keys (:secret-code), :msg "must be a number"}), …}

(fp/parse-params example-form {:secret-code "xxx"} :validate false)
{:secret-code #formative.parse.ParseError{:bad-value "xxx"}}
```

The `formative.parse/with-fallback` macro is a handy way to try parsing Ring params, and call a "fallback" function when it fails. The fallback function will be supplied the problems as its argument.

```clj
(defn submit-example-form [params]
  ;; Calls (show-example-form params :problems problems) if parsing fails
  (fp/with-fallback (partial show-example-form params :problems)
    (let [values (fp/parse-params example-form params))]
      ;; Success
      (prn-str values)))
```

### Validating Parsed Data

Formative uses [Verily](https://github.com/jkk/verily) to validate parsed data. By default, only datatypes are validated. There are two ways to add your own validation to a form: `:validations` and `:validator`.

#### `:validations`

A sequence of validation specifications. For example:

```clj
[[:required [:foo :bar :password]]
 [:equal [:password :confirm-password] "Passwords don't match, dummy"]
 [:min-length 8 :password]]
```

All validation specifications accept a key or sequence of keys. The message is always optional.

Unless `:required` is used, all validations allow the keys to be absent from the map, or have a `nil` value (or blank if a string-based type).

Built in validations:

* `:required <keys> [msg]` - must not absent, blank, or nil
* `:contains <keys> [msg]` - must not be absent, but can be blank or nil
* `:not-blank <keys> [msg]` - may be absent but not blank or nil
* `:exact <value> <keys> [msg]` - must be a particular value
* `:equal <keys> [msg]` - all keys must be equal
* `:email <keys> [msg]` - must be a valid email
* `:url <keys> [msg]` - must be a valid URL
* `:web-url <keys> [msg]` - must be a valid website URL (http or https)
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
* Datatype validations: `:string`, `:boolean`, `:integer`, `:float`, `:decimal`, `:date`
* Datatype collection validations: `:strings`, `:booleans`, `:integers`, `:floats`, `:decimals`, `:dates`

#### `:validator`

A function that takes a map of parsed values and returns a problem map or sequence of problem maps. A problem map has the keys `:keys`, indicating which keys were problems (optional), and `:msg`, a description of what's wrong. If `nil` or an empty sequence is returned, validation succeeds. 

```clj
(defn validate-password [values]
  (when (#{"12345" "password" "hunter2"} (:password values))
    {:keys [:password] :msg "You can't use that password"}))
```

See [Verily](https://github.com/jkk/verily) for more about validation functions.

## Quick Reference

### Form Specification

Valid keys for a form specification include the following HTML form attributes:

      :action :method :enctype :accept :name :id :class
      :onsubmit :onreset :accept-charset :autofill :novalidate
      :autocomplete

Unlike an HTML form, :method defaults to :post.

The following special keys are also supported:

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
                      
New form renderers can be implemented using the `formative.render/render-form` multimethod.

### Field Specification

A field specification is a map with keys corresponding to HTML attributes and
the following special keys:

      :name         - Required name of the field, a keyword or string
      :label        - Optional display name. Auto-generated from :name if not
                      provided
      :type         - Type of the field. Defaults to :text. See below for
                      built-in types. If an unrecognized type is provided,
                      an <input> element with that type will be assumed.
                      Certain types imply particular parsing or validation
                      rules - e.g., an :email field must be a valid email.
      :datatype     - Optional. Datatype of the field used for parsing. Can be
                      one of:

                      :str, :int, :long, :boolean, :float, :double, :decimal,
                      :bigint, :date, :file.

                      Defaults to :str.

                      All types can be appended with an "s" when a sequence
                      is expected - e.g., :ints for a sequence of integers. This
                      is useful for fields that have composite values, such as
                      :checkboxes.

                      :date field values are expected to be in yyyy-MM-dd
                      format by default. Set :date-format to change that.
      :note         - A bit of explanatory content to accompany the field
      :prefix       - Content to insert before a field
      :suffix       - Content to insert after a field

### Field Types

Built-in field types:

	  :text         - Single-line text input
	  :textarea     - Multi-line text input
	  :select       - Dropdown. Special keys:
                        :options - options to display; see below for format
                        :placeholder - text for a first, disabled option
                        :first-option - option to prepend to the other options
      :checkbox     - Defaults to true/false when no :value is given. Special
                      keys:
                        :value - value of a checked input (default true)
                        :unchecked-value - value of an unchecked input (default
                          false)
      :checkboxes   - Multiple checkboxes that parse to a collection of values.
                      Special keys:
                        :options - options to display; see below for format
                        :cols - number of columns to group checkboxes into
      :radios       - Multiple radio inputs that parse to a single value.
                      Special keys:
                        :options - options to display; see below for format
      :email        - Email text input
      :us-state     - United States state dropdown. Accepts :select special
                      keys.
      :us-zip       - United States ZIP code
      :ca-state     - Canadian province
      :country      - Country dropdown. Accepts :select special keys.
      :date-select  - Date dropdown. Renders as multiple :select fields, parses
                      as a java.util.Date. Special keys:
                        :year-start
                        :year-end
      :year-select  - Year dropdown, parses to integer. Accepts :select special
                      keys plus:
                        :start
                        :end
      :month-select - Month dropdown, parses to integer (1-12). Accepts :select
                      special keys plus:
                        :numbers - when true, shows numbers instead of month
                                   names
      :currency     - Text input for money. Parses as a :decimal datatype
      :file         - File upload input. Special keys:
                        :upload-handler - optional handler called when a file is
                          uploaded. The field's specification and Ring param
                          value are passed as arguments to the handler. The
                          handler can return whatever value is appropriate
                          (e.g., a String or a File).
      :submit       - Submit button. Included by default, but can be added
                      explicitly if you prefer. Unlike with a default submit
                      button, its value will be parsed.

The :options key for :select and other types accepts a collection of any of the following formats:

      ["value" "label"]
      {:value "value" :label "label"}
      "value and label"

The following presentational types are also available. They are excluded from parsing.

      :heading      - Section heading. In the default renderer, acts as a fieldset
                      separator. Special keys:
                        :text - heading text
      :html         - Custom, unlabeled HTML. Special keys:
                        :html - HTML string or Hiccup data
      :labeled-html - Custom, labeled HTML. Special keys:
                        :html - HTML string or Hiccup data

Field types are extensible with the `formative.render/render-field` and `formative.parse/parse-input` multimethods.

## Demo

See a live demo at [formative-demo.herokuapp.com](http://formative-demo.herokuapp.com) or [view the demo source](https://github.com/jkk/formative-demo).

## License

Copyright © 2012 Justin Kramer

Distributed under the Eclipse Public License, the same as Clojure.
