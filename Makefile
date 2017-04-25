%.svg: %.dot
	dot -Tsvg $< -o$@

DOT_FILES=$(shell ls doc/*.dot)
SVG_FILES=$(DOT_FILES:.dot=.svg)


images: $(SVG_FILES)

doc/design-and-implementation/presentation.pdf: doc/design-and-implementation/presentation.org

	emacs --script doc/design-and-implementation/convert.el

docs: images doc/design-and-implementation/presentation.pdf
	lein do clean, codox
	cp doc/*.svg target/doc/
	mkdir -p target/doc/design-and-implementation
	cp doc/design-and-implementation/presentation.pdf target/doc/design-and-implementation/presentation.pdf



gh-pages: docs
	rm -rf gh-pages/*
	cp -rf target/doc/* gh-pages/

.PHONY: docs test

test:
	lein test

start-mysql:
	docker run -d -e MYSQL_ALLOW_EMPTY_PASSWORD=true mysql:8
