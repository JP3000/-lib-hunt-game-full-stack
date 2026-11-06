"use client";

import { useState } from "react";

type StoryClueBoxProps = {
  videoUrl?: string;
  imageUrl?: string;
  imageAlt?: string;
  imageCaption?: string;
};

export function StoryClueBox({ videoUrl, imageUrl, imageAlt, imageCaption }: StoryClueBoxProps) {
  const [isPreviewOpen, setIsPreviewOpen] = useState(false);

  return (
    <div className="mt-4 space-y-3">
      {videoUrl ? (
        <div
          className="overflow-hidden rounded-xl bg-black/25"
          style={{ border: "1px solid var(--border)" }}
        >
          <video controls playsInline className="w-full">
            <source src={videoUrl} type="video/mp4" />
          </video>
        </div>
      ) : null}

      {imageUrl ? (
        <div className="overflow-hidden rounded-xl bg-black/20 p-2" style={{ border: "1px solid var(--border)" }}>
          <button
            type="button"
            onClick={() => setIsPreviewOpen(true)}
            className="block w-full overflow-hidden rounded-lg bg-black/30 text-left"
            style={{ border: "1px solid var(--border)" }}
            aria-label={`查看大图：${imageAlt}`}
          >
            <img src={imageUrl} alt={imageAlt} className="h-auto w-full object-cover" />
          </button>
          {imageCaption ? (
            <p className="px-1 pb-1 pt-2" style={{ color: "var(--ink-muted)", fontSize: "0.75rem" }}>
              {imageCaption}
            </p>
          ) : null}
        </div>
      ) : null}

      {isPreviewOpen && imageUrl ? (
        <button
          type="button"
          onClick={() => setIsPreviewOpen(false)}
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 p-4 text-left"
          aria-label="关闭大图预览"
        >
          <div className="max-h-[90vh] w-full max-w-5xl overflow-hidden rounded-2xl bg-black/90 shadow-2xl">
            <img src={imageUrl} alt={imageAlt} className="max-h-[90vh] w-full object-contain" />
            {imageCaption ? (
              <div className="border-t border-white/10 px-4 py-3 text-sm text-white/80">
                {imageCaption}
              </div>
            ) : null}
          </div>
        </button>
      ) : null}
    </div>
  );
}