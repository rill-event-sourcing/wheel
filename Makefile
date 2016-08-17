%.svg: %.dot
	dot -Tsvg $< -o$@

DOT_FILES=$(shell ls doc/*.dot)
SVG_FILES=$(DOT_FILES:.dot=.svg)


images: $(SVG_FILES)

docs: images
	lein do clean, codox
	cp doc/*.svg target/doc/

gh-pages: docs
	rm -rf gh-pages/*
	cp -rf target/doc/* gh-pages/
