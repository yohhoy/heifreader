#pragma version(1)
#pragma rs java_package_name(jp.yohhoy.heifreader)
#pragma rs_fp_relaxed

rs_allocation gYUV;

uchar4 RS_KERNEL convert(uint32_t x, uint32_t y) {
  uchar luma = rsGetElementAtYuv_uchar_Y(gYUV, x, y);
  uchar u = rsGetElementAtYuv_uchar_U(gYUV, x, y);
  uchar v = rsGetElementAtYuv_uchar_V(gYUV, x, y);
  return rsYuvToRGBA_uchar4(luma, u, v);
}
