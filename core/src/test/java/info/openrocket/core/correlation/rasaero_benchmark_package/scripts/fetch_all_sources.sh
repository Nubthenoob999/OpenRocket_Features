#!/usr/bin/env bash
set -euo pipefail
mkdir -p source_pdfs
cd source_pdfs

curl -L -o A53D02.pdf "https://www.rasaero.com/dloads/NACA%20RM%20A53D02.pdf"
curl -L -o TR-R-100.pdf "https://www.rasaero.com/dloads/NASA%20TR%20R-100.pdf"
curl -L -o RASAero-ARCAS-comparison.pdf "https://www.rasaero.com/dloads/RASAero%20II%20Comparisons%20with%20ARCAS%20CP%20and%20CD%20Data.pdf"
curl -L -o D-4013.pdf "https://www.rasaero.com/dloads/ARCAS%20NASA%20TN%20D-4013.pdf"
curl -L -o Aerobee-150A.pdf "https://www.rasaero.com/dloads/Aerobee%20150A%20-%20Vought%20Astronautics%20Report%20AST%20E1R-13319.pdf"

curl -L -o 19670020031.pdf "https://ntrs.nasa.gov/api/citations/19670020031/downloads/19670020031.pdf"
curl -L -o 19650000798.pdf "https://ntrs.nasa.gov/api/citations/19650000798/downloads/19650000798.pdf"
curl -L -o 19930087140.pdf "https://ntrs.nasa.gov/api/citations/19930087140/downloads/19930087140.pdf"
curl -L -o 19930089121.pdf "https://ntrs.nasa.gov/api/citations/19930089121/downloads/19930089121.pdf"
curl -L -o 19930088274.pdf "https://ntrs.nasa.gov/api/citations/19930088274/downloads/19930088274.pdf"
curl -L -o 19670022660.pdf "https://ntrs.nasa.gov/api/citations/19670022660/downloads/19670022660.pdf"
curl -L -o 19780012129.pdf "https://ntrs.nasa.gov/api/citations/19780012129/downloads/19780012129.pdf"

sha256sum *.pdf > SHA256SUMS.txt
