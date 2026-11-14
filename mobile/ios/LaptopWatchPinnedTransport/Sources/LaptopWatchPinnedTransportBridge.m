#import <React/RCTBridgeModule.h>

@interface RCT_EXTERN_MODULE(LaptopWatchPinnedTransport, NSObject)
RCT_EXTERN_METHOD(downloadPinned:(NSDictionary *)params resolver:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject)
RCT_EXTERN_METHOD(sendPinned:(NSDictionary *)params resolver:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject)
@end
