#!/usr/local/bin/python

import os
import urllib2
from urllib2 import urlopen, URLError, HTTPError
import zipfile
from bs4 import BeautifulSoup

def dlfile(url):
    try:
        f = urlopen(url)
        print "downloading " + url

        with open(os.path.basename(url), "wb") as local_file:
            local_file.write(f.read())

    except HTTPError, e:
        print "HTTP Error:", e.code, url
    except URLError, e:
        print "URL Error:", e.reason, url

all_content_html = urllib2.urlopen("https://nexus-dev.tax.service.gov.uk/service/local/repositories/hmrc-releases/content/uk/gov/hmrc/assets-frontend/").read()

soup = BeautifulSoup(all_content_html)
content_item = soup.findAll("content-item")

for item in content_item:
    version = item.find("text").text
    nexus_url = 'https://nexus-dev.tax.service.gov.uk/service/local/repositories/hmrc-releases/content/uk/gov/hmrc/assets-frontend/' + version + '/assets-frontend-' + version + '.zip'
    print 'downloading:' + nexus_url 
    dlfile(nexus_url)
    print 'unzipping assets-frontend:' + version 
    zipfile.ZipFile('assets-frontend-' + version + '.zip', 'r').extractall('/var/lib/nginx/assets/' + version)

print 'Completed download of assets-frontend.'