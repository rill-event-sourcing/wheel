(defun export-presentation (in)
  (find-file in)
  (require 'ox-beamer)
  (setq org-confirm-babel-evaluate nil)
  (org-beamer-export-to-pdf))

(defun test-export-presentation ()
  (interactive)
  (export-presentation "presentation.org"))

(export-presentation "presentation.org")
