#!/system/bin/sh
# Start the ZLink service as early as Android allows (needs system_server's activity service).
# Component names are PLACEHOLDERS - confirm with: pm list packages | grep -i link
ZLINK=com.zjinnova.zlink/.ZLinkService
UI=com.ridgeline.dock/.boot.ZLinkEarlyService
until service check activity | grep -q found; do sleep 0.2; done
am start-foreground-service -n $ZLINK
am start-foreground-service -n $UI
