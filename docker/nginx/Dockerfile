FROM nginx

WORKDIR /root

RUN \
	apt-get update && \
	apt-get install -y python && \
	apt-get install -y wget && \
	wget https://bootstrap.pypa.io/get-pip.py --no-check-certificate && \
    python get-pip.py

RUN \
	pip install urllib3 && \
	pip install beautifulsoup4

ADD download-assets.py /root/download-assets.py
RUN python /root/download-assets.py

COPY etc/nginx/conf.d/default.conf /etc/nginx/conf.d/default.conf
