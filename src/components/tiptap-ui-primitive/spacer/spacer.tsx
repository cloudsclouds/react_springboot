// @ts-nocheck
"use client";
import { type CSSProperties, type HTMLAttributes } from "react"

type SpacerProps = HTMLAttributes<HTMLDivElement> & {
  orientation?: "horizontal" | "vertical"
  size?: string | number
  style?: CSSProperties
}

export function Spacer({
  orientation = "horizontal",
  size,
  style = {},
  ...props
}: SpacerProps) {
  const computedStyle = {
    ...style,
    ...(orientation === "horizontal" && !size && { flex: 1 }),
    ...(size && {
      width: orientation === "vertical" ? "1px" : size,
      height: orientation === "horizontal" ? "1px" : size,
    }),
  }

  return <div {...props} style={computedStyle} />;
}